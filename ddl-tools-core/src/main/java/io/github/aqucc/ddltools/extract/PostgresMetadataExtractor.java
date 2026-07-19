package io.github.aqucc.ddltools.extract;

import static io.github.aqucc.ddltools.extract.ExtractorSupport.inList;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.intOf;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.longOf;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.str;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.model.CheckConstraintMetadata;
import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.ForeignKeyMetadata;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.IndexMetadata;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.ParameterMetadata;
import io.github.aqucc.ddltools.model.PrimaryKeyMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;
import io.github.aqucc.ddltools.model.UniqueConstraintMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;

/**
 * PostgreSQLのカタログ(information_schema / pg_catalog)からメタ情報を抽出する。
 * PostgreSQL 14以降を対象とする。
 */
public class PostgresMetadataExtractor implements MetadataExtractor {

    private final RowSource rowSource;
    private final List<String> warnings = new ArrayList<String>();

    public PostgresMetadataExtractor(RowSource rowSource) {
        this.rowSource = rowSource;
    }

    @Override
    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public DatabaseMetadata extract(List<String> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            schemas = Arrays.asList("public");
        }
        DatabaseMetadata db = new DatabaseMetadata(Dialect.POSTGRESQL);
        db.setExtractedAt(Instant.now().toString());
        String in = inList(schemas);

        extractTables(db, in);
        extractConstraints(db, in);
        extractIndexes(db, in);
        extractComments(db, in);
        extractViews(db, in);
        extractMaterializedViews(db, in);
        extractTriggers(db, in);
        extractRoutines(db, in);
        extractSequences(db, in);
        extractOthers(db, in);
        return db;
    }

    private void extractTables(DatabaseMetadata db, String in) {
        for (Map<String, Object> t : rowSource.query(
                "SELECT table_schema, table_name FROM information_schema.tables"
                        + " WHERE table_schema IN (" + in + ") AND table_type = 'BASE TABLE'"
                        + " ORDER BY table_schema, table_name")) {
            db.findOrCreateSchema(str(t, "table_schema")).getTables()
                    .add(new TableMetadata(str(t, "table_name")));
        }
        for (Map<String, Object> c : rowSource.query(
                "SELECT table_schema, table_name, column_name, udt_name, data_type,"
                        + " character_maximum_length, numeric_precision, numeric_scale,"
                        + " is_nullable, column_default, is_identity"
                        + " FROM information_schema.columns WHERE table_schema IN (" + in + ")"
                        + " ORDER BY table_schema, table_name, ordinal_position")) {
            TableMetadata table = findTable(db, str(c, "table_schema"), str(c, "table_name"));
            if (table == null) {
                continue;
            }
            ColumnMetadata col = new ColumnMetadata(str(c, "column_name"), str(c, "udt_name"));
            col.setLength(intOf(c, "character_maximum_length"));
            if ("numeric".equalsIgnoreCase(str(c, "udt_name"))) {
                col.setPrecision(intOf(c, "numeric_precision"));
                col.setScale(intOf(c, "numeric_scale"));
            }
            col.setNullable(!"NO".equalsIgnoreCase(str(c, "is_nullable")));
            String def = str(c, "column_default");
            if (def != null) {
                col.setDefaultValue(def);
            }
            if ("YES".equalsIgnoreCase(str(c, "is_identity"))
                    || (def != null && def.startsWith("nextval("))) {
                col.setIdentity(Boolean.TRUE);
            }
            table.getColumns().add(col);
        }
    }

    private void extractConstraints(DatabaseMetadata db, String in) {
        // 制約カラム (ordinal順)
        Map<String, List<String>> keyColumns = new HashMap<String, List<String>>();
        for (Map<String, Object> kcu : rowSource.query(
                "SELECT constraint_schema, constraint_name, column_name"
                        + " FROM information_schema.key_column_usage"
                        + " WHERE constraint_schema IN (" + in + ")"
                        + " ORDER BY constraint_schema, constraint_name, ordinal_position")) {
            String key = str(kcu, "constraint_schema") + "|" + str(kcu, "constraint_name");
            List<String> cols = keyColumns.get(key);
            if (cols == null) {
                cols = new ArrayList<String>();
                keyColumns.put(key, cols);
            }
            cols.add(str(kcu, "column_name"));
        }

        for (Map<String, Object> tc : rowSource.query(
                "SELECT constraint_schema, constraint_name, table_schema, table_name,"
                        + " constraint_type FROM information_schema.table_constraints"
                        + " WHERE table_schema IN (" + in + ")"
                        + " AND constraint_type IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY')"
                        + " ORDER BY table_schema, table_name, constraint_name")) {
            TableMetadata table = findTable(db, str(tc, "table_schema"), str(tc, "table_name"));
            if (table == null) {
                continue;
            }
            String name = str(tc, "constraint_name");
            String type = str(tc, "constraint_type");
            List<String> columns = keyColumns.get(str(tc, "constraint_schema") + "|" + name);
            if (columns == null) {
                columns = new ArrayList<String>();
            }
            if ("PRIMARY KEY".equals(type)) {
                table.setPrimaryKey(new PrimaryKeyMetadata(name, columns));
            } else if ("UNIQUE".equals(type)) {
                table.getUniqueConstraints().add(new UniqueConstraintMetadata(name, columns));
            } else if ("FOREIGN KEY".equals(type)) {
                ForeignKeyMetadata fk = new ForeignKeyMetadata();
                fk.setName(name);
                fk.setColumns(columns);
                List<Map<String, Object>> ref = rowSource.query(
                        "SELECT ccu.table_schema, ccu.table_name, ccu.column_name"
                                + " FROM information_schema.referential_constraints rc"
                                + " JOIN information_schema.constraint_column_usage ccu"
                                + " ON ccu.constraint_schema = rc.unique_constraint_schema"
                                + " AND ccu.constraint_name = rc.unique_constraint_name"
                                + " WHERE rc.constraint_schema = ? AND rc.constraint_name = ?",
                        str(tc, "constraint_schema"), name);
                if (!ref.isEmpty()) {
                    fk.setReferencedSchema(str(ref.get(0), "table_schema"));
                    fk.setReferencedTable(str(ref.get(0), "table_name"));
                    List<String> refCols = new ArrayList<String>();
                    for (Map<String, Object> r : ref) {
                        refCols.add(str(r, "column_name"));
                    }
                    fk.setReferencedColumns(refCols);
                }
                table.getForeignKeys().add(fk);
            }
        }

        for (Map<String, Object> chk : rowSource.query(
                "SELECT tc.table_schema, tc.table_name, cc.constraint_name, cc.check_clause"
                        + " FROM information_schema.check_constraints cc"
                        + " JOIN information_schema.table_constraints tc"
                        + " ON tc.constraint_schema = cc.constraint_schema"
                        + " AND tc.constraint_name = cc.constraint_name"
                        + " WHERE tc.table_schema IN (" + in + ")"
                        + " AND tc.constraint_type = 'CHECK'")) {
            TableMetadata table = findTable(db, str(chk, "table_schema"), str(chk, "table_name"));
            if (table == null) {
                continue;
            }
            String clause = str(chk, "check_clause");
            // NOT NULL制約由来のCHECK (col IS NOT NULL) は除外
            if (clause != null && !clause.trim().toUpperCase().endsWith("IS NOT NULL")) {
                table.getCheckConstraints().add(
                        new CheckConstraintMetadata(str(chk, "constraint_name"), clause.trim()));
            }
        }
    }

    private void extractIndexes(DatabaseMetadata db, String in) {
        for (Map<String, Object> ix : rowSource.query(
                "SELECT n.nspname AS schema_name, t.relname AS table_name,"
                        + " i.relname AS index_name, x.indisunique AS is_unique,"
                        + " pg_get_indexdef(i.oid) AS indexdef"
                        + " FROM pg_index x"
                        + " JOIN pg_class i ON i.oid = x.indexrelid"
                        + " JOIN pg_class t ON t.oid = x.indrelid"
                        + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                        + " WHERE n.nspname IN (" + in + ")"
                        + " AND NOT x.indisprimary"
                        + " AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = i.oid)"
                        + " ORDER BY n.nspname, t.relname, i.relname")) {
            TableMetadata table = findTable(db, str(ix, "schema_name"), str(ix, "table_name"));
            if (table == null) {
                continue;
            }
            boolean unique = Boolean.TRUE.equals(ix.get("is_unique"))
                    || "t".equalsIgnoreCase(str(ix, "is_unique"))
                    || "true".equalsIgnoreCase(str(ix, "is_unique"));
            table.getIndexes().add(new IndexMetadata(str(ix, "index_name"), unique,
                    columnsFromIndexDef(str(ix, "indexdef"))));
            if (unique) {
                table.getUniqueConstraints().add(new UniqueConstraintMetadata(
                        str(ix, "index_name"), columnsFromIndexDef(str(ix, "indexdef"))));
            }
        }
    }

    /** pg_get_indexdef の結果からカラムリストを取り出す。 */
    private List<String> columnsFromIndexDef(String indexdef) {
        List<String> columns = new ArrayList<String>();
        if (indexdef == null) {
            return columns;
        }
        int open = indexdef.indexOf('(');
        int close = indexdef.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return columns;
        }
        for (String c : indexdef.substring(open + 1, close).split(",")) {
            columns.add(c.trim().replace("\"", ""));
        }
        return columns;
    }

    private void extractComments(DatabaseMetadata db, String in) {
        for (Map<String, Object> tc : rowSource.query(
                "SELECT n.nspname AS schema_name, c.relname AS table_name,"
                        + " obj_description(c.oid) AS comment"
                        + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname IN (" + in + ") AND c.relkind IN ('r', 'p')"
                        + " AND obj_description(c.oid) IS NOT NULL")) {
            TableMetadata table = findTable(db, str(tc, "schema_name"), str(tc, "table_name"));
            if (table != null) {
                table.setComment(str(tc, "comment"));
            }
        }
        for (Map<String, Object> cc : rowSource.query(
                "SELECT n.nspname AS schema_name, c.relname AS table_name,"
                        + " a.attname AS column_name, col_description(c.oid, a.attnum) AS comment"
                        + " FROM pg_attribute a"
                        + " JOIN pg_class c ON c.oid = a.attrelid"
                        + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " WHERE n.nspname IN (" + in + ") AND c.relkind IN ('r', 'p')"
                        + " AND a.attnum > 0 AND NOT a.attisdropped"
                        + " AND col_description(c.oid, a.attnum) IS NOT NULL")) {
            TableMetadata table = findTable(db, str(cc, "schema_name"), str(cc, "table_name"));
            if (table != null) {
                ColumnMetadata col = table.findColumn(str(cc, "column_name"));
                if (col != null) {
                    col.setComment(str(cc, "comment"));
                }
            }
        }
    }

    private void extractViews(DatabaseMetadata db, String in) {
        for (Map<String, Object> v : rowSource.query(
                "SELECT schemaname, viewname, definition FROM pg_views"
                        + " WHERE schemaname IN (" + in + ") ORDER BY schemaname, viewname")) {
            db.findOrCreateSchema(str(v, "schemaname")).getViews()
                    .add(new ViewMetadata(str(v, "viewname"), stripTrailingSemicolon(str(v, "definition"))));
        }
    }

    private void extractMaterializedViews(DatabaseMetadata db, String in) {
        for (Map<String, Object> mv : rowSource.query(
                "SELECT schemaname, matviewname, definition FROM pg_matviews"
                        + " WHERE schemaname IN (" + in + ") ORDER BY schemaname, matviewname")) {
            db.findOrCreateSchema(str(mv, "schemaname")).getMaterializedViews()
                    .add(new MaterializedViewMetadata(str(mv, "matviewname"),
                            stripTrailingSemicolon(str(mv, "definition"))));
        }
    }

    private static String stripTrailingSemicolon(String sql) {
        if (sql == null) {
            return null;
        }
        String s = sql.trim();
        return s.endsWith(";") ? s.substring(0, s.length() - 1) : s;
    }

    private void extractTriggers(DatabaseMetadata db, String in) {
        // 1トリガー=複数行 (イベントごと) で返るため名前でまとめる
        Map<String, TriggerMetadata> byName = new HashMap<String, TriggerMetadata>();
        for (Map<String, Object> t : rowSource.query(
                "SELECT trigger_schema, trigger_name, action_timing, event_manipulation,"
                        + " event_object_table, action_orientation, action_statement"
                        + " FROM information_schema.triggers WHERE trigger_schema IN (" + in + ")"
                        + " ORDER BY trigger_schema, trigger_name, event_manipulation")) {
            String key = str(t, "trigger_schema") + "|" + str(t, "trigger_name");
            TriggerMetadata trigger = byName.get(key);
            if (trigger == null) {
                trigger = new TriggerMetadata(str(t, "trigger_name"));
                trigger.setTiming(str(t, "action_timing"));
                trigger.setTargetTable(str(t, "event_object_table"));
                trigger.setLevel(str(t, "action_orientation"));
                trigger.setBody(str(t, "action_statement"));
                byName.put(key, trigger);
                db.findOrCreateSchema(str(t, "trigger_schema")).getTriggers().add(trigger);
            }
            String event = str(t, "event_manipulation");
            if (event != null && !trigger.getEvents().contains(event)) {
                trigger.getEvents().add(event);
            }
        }
    }

    private void extractRoutines(DatabaseMetadata db, String in) {
        for (Map<String, Object> r : rowSource.query(
                "SELECT n.nspname AS schema_name, p.proname AS routine_name,"
                        + " CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END AS routine_type,"
                        + " pg_get_function_result(p.oid) AS return_type,"
                        + " pg_get_function_arguments(p.oid) AS arguments,"
                        + " pg_get_functiondef(p.oid) AS definition"
                        + " FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace"
                        + " WHERE n.nspname IN (" + in + ") AND p.prokind IN ('f', 'p')"
                        + " ORDER BY n.nspname, p.proname")) {
            RoutineMetadata routine = new RoutineMetadata(
                    str(r, "routine_name"), str(r, "routine_type"));
            if ("FUNCTION".equals(routine.getRoutineType())) {
                routine.setReturnType(str(r, "return_type"));
            }
            parseArguments(str(r, "arguments"), routine);
            routine.setSourceText(str(r, "definition"));
            db.findOrCreateSchema(str(r, "schema_name")).getRoutines().add(routine);
        }
    }

    /** pg_get_function_arguments の結果 ("p_id bigint, OUT total numeric") を分解する。 */
    private void parseArguments(String arguments, RoutineMetadata routine) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return;
        }
        for (String raw : arguments.split(",")) {
            String p = raw.trim().replaceAll("(?i)\\s+DEFAULT\\s+.*$", "");
            if (p.isEmpty()) {
                continue;
            }
            List<String> tokens = new ArrayList<String>(Arrays.asList(p.split("\\s+")));
            String direction = "IN";
            if (!tokens.isEmpty()) {
                String first = tokens.get(0).toUpperCase();
                if (first.equals("IN") || first.equals("OUT") || first.equals("INOUT")
                        || first.equals("VARIADIC")) {
                    if (!first.equals("VARIADIC")) {
                        direction = first;
                    }
                    tokens.remove(0);
                }
            }
            ParameterMetadata param = new ParameterMetadata();
            param.setDirection(direction);
            if (tokens.size() >= 2) {
                param.setName(tokens.get(0));
                param.setTypeName(joinTokens(tokens, 1));
            } else if (tokens.size() == 1) {
                param.setTypeName(tokens.get(0));
            }
            routine.getParameters().add(param);
        }
    }

    private static String joinTokens(List<String> tokens, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < tokens.size(); i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(tokens.get(i));
        }
        return sb.toString();
    }

    private void extractSequences(DatabaseMetadata db, String in) {
        for (Map<String, Object> s : rowSource.query(
                "SELECT schemaname, sequencename, start_value, increment_by, min_value,"
                        + " max_value, cycle FROM pg_sequences WHERE schemaname IN (" + in + ")"
                        + " ORDER BY schemaname, sequencename")) {
            SequenceMetadata seq = new SequenceMetadata(str(s, "sequencename"));
            seq.setStartValue(longOf(s, "start_value"));
            seq.setIncrementBy(longOf(s, "increment_by"));
            seq.setMinValue(longOf(s, "min_value"));
            seq.setMaxValue(longOf(s, "max_value"));
            Object cycle = s.get("cycle");
            if (cycle != null) {
                seq.setCycle(Boolean.TRUE.equals(cycle) || "t".equalsIgnoreCase(cycle.toString())
                        || "true".equalsIgnoreCase(cycle.toString()));
            }
            db.findOrCreateSchema(str(s, "schemaname")).getSequences().add(seq);
        }
    }

    private void extractOthers(DatabaseMetadata db, String in) {
        for (Map<String, Object> d : rowSource.query(
                "SELECT domain_schema, domain_name, data_type FROM information_schema.domains"
                        + " WHERE domain_schema IN (" + in + ") ORDER BY domain_schema, domain_name")) {
            db.findOrCreateSchema(str(d, "domain_schema")).getOthers().add(
                    new GenericObjectMetadata(str(d, "domain_name"), "DOMAIN",
                            "AS " + str(d, "data_type")));
        }
        for (Map<String, Object> ft : rowSource.query(
                "SELECT foreign_table_schema, foreign_table_name"
                        + " FROM information_schema.foreign_tables"
                        + " WHERE foreign_table_schema IN (" + in + ")")) {
            db.findOrCreateSchema(str(ft, "foreign_table_schema")).getOthers().add(
                    new GenericObjectMetadata(str(ft, "foreign_table_name"), "FOREIGN TABLE", null));
        }
    }

    private TableMetadata findTable(DatabaseMetadata db, String schemaName, String tableName) {
        SchemaMetadata schema = db.findSchema(schemaName);
        return schema == null ? null : schema.findTable(tableName);
    }
}
