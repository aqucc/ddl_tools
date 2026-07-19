package io.github.aqucc.ddltools.extract;

import static io.github.aqucc.ddltools.extract.ExtractorSupport.bool;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.inList;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.intOf;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.longOf;
import static io.github.aqucc.ddltools.extract.ExtractorSupport.str;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.model.CheckConstraintMetadata;
import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.model.ForeignKeyMetadata;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.IndexMetadata;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.PackageMetadata;
import io.github.aqucc.ddltools.model.ParameterMetadata;
import io.github.aqucc.ddltools.model.PrimaryKeyMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.SynonymMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;
import io.github.aqucc.ddltools.model.UniqueConstraintMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;

/**
 * Oracleの辞書ビュー(ALL_*)からメタ情報を抽出する。
 * 11gに存在するビューのみを使用する。
 */
public class OracleMetadataExtractor implements MetadataExtractor {

    /** 個別に構造化するオブジェクト種別。それ以外はGenericObjectMetadataへ。 */
    private static final Set<String> STRUCTURED_TYPES = new HashSet<String>(Arrays.asList(
            "TABLE", "VIEW", "MATERIALIZED VIEW", "INDEX", "SEQUENCE", "TRIGGER",
            "PROCEDURE", "FUNCTION", "PACKAGE", "PACKAGE BODY", "SYNONYM",
            "LOB", "TABLE PARTITION", "INDEX PARTITION", "LOB PARTITION"));

    private final RowSource rowSource;
    private final List<String> warnings = new ArrayList<String>();

    public OracleMetadataExtractor(RowSource rowSource) {
        this.rowSource = rowSource;
    }

    @Override
    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public DatabaseMetadata extract(List<String> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            throw new IllegalArgumentException("schemas must not be empty for Oracle");
        }
        DatabaseMetadata db = new DatabaseMetadata(Dialect.ORACLE);
        db.setExtractedAt(Instant.now().toString());
        String owners = inList(schemas);

        extractTables(db, owners);
        extractConstraints(db, owners);
        extractIndexes(db, owners);
        extractComments(db, owners);
        extractViews(db, owners);
        extractMaterializedViews(db, owners);
        extractTriggers(db, owners);
        extractRoutines(db, owners);
        extractSequences(db, owners);
        extractSynonyms(db, owners);
        extractPackages(db, owners);
        extractOthers(db, owners);
        return db;
    }

    private void extractTables(DatabaseMetadata db, String owners) {
        for (Map<String, Object> t : rowSource.query(
                "SELECT owner, table_name FROM all_tables WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, table_name")) {
            db.findOrCreateSchema(str(t, "owner")).getTables()
                    .add(new TableMetadata(str(t, "table_name")));
        }
        for (Map<String, Object> c : rowSource.query(
                "SELECT owner, table_name, column_name, data_type, char_length, data_length,"
                        + " data_precision, data_scale, nullable, data_default"
                        + " FROM all_tab_columns WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, table_name, column_id")) {
            TableMetadata table = findTable(db, str(c, "owner"), str(c, "table_name"));
            if (table == null) {
                continue;
            }
            ColumnMetadata col = new ColumnMetadata(str(c, "column_name"), str(c, "data_type"));
            Integer charLength = intOf(c, "char_length");
            if (charLength != null && charLength > 0) {
                col.setLength(charLength);
            }
            col.setPrecision(intOf(c, "data_precision"));
            col.setScale(intOf(c, "data_scale"));
            col.setNullable(!"N".equalsIgnoreCase(str(c, "nullable")));
            String def = str(c, "data_default");
            if (def != null && !def.trim().isEmpty()) {
                col.setDefaultValue(def.trim());
            }
            table.getColumns().add(col);
        }
    }

    private void extractConstraints(DatabaseMetadata db, String owners) {
        // 制約カラムを (owner, constraint_name) → カラム名リスト に集約
        Map<String, List<String>> consColumns = new HashMap<String, List<String>>();
        Map<String, String[]> consTable = new HashMap<String, String[]>();
        for (Map<String, Object> cc : rowSource.query(
                "SELECT owner, constraint_name, table_name, column_name"
                        + " FROM all_cons_columns WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, constraint_name, position")) {
            String key = str(cc, "owner") + "|" + str(cc, "constraint_name");
            List<String> cols = consColumns.get(key);
            if (cols == null) {
                cols = new ArrayList<String>();
                consColumns.put(key, cols);
            }
            cols.add(str(cc, "column_name"));
            consTable.put(key, new String[] {str(cc, "owner"), str(cc, "table_name")});
        }

        for (Map<String, Object> con : rowSource.query(
                "SELECT owner, constraint_name, table_name, constraint_type,"
                        + " r_owner, r_constraint_name, search_condition"
                        + " FROM all_constraints WHERE owner IN (" + owners + ")"
                        + " AND constraint_type IN ('P', 'R', 'U', 'C')"
                        + " ORDER BY owner, table_name, constraint_name")) {
            TableMetadata table = findTable(db, str(con, "owner"), str(con, "table_name"));
            if (table == null) {
                continue;
            }
            String name = str(con, "constraint_name");
            String type = str(con, "constraint_type");
            List<String> columns = consColumns.get(str(con, "owner") + "|" + name);
            if ("P".equals(type)) {
                table.setPrimaryKey(new PrimaryKeyMetadata(name,
                        columns == null ? new ArrayList<String>() : columns));
            } else if ("U".equals(type)) {
                table.getUniqueConstraints().add(new UniqueConstraintMetadata(name,
                        columns == null ? new ArrayList<String>() : columns));
            } else if ("C".equals(type)) {
                String condition = str(con, "search_condition");
                // NOT NULL制約由来のCHECKは除外
                if (condition != null && !condition.trim().toUpperCase().endsWith("IS NOT NULL")) {
                    table.getCheckConstraints().add(new CheckConstraintMetadata(name, condition.trim()));
                }
            } else if ("R".equals(type)) {
                ForeignKeyMetadata fk = new ForeignKeyMetadata();
                fk.setName(name);
                if (columns != null) {
                    fk.setColumns(columns);
                }
                String refKey = str(con, "r_owner") + "|" + str(con, "r_constraint_name");
                String[] refTable = consTable.get(refKey);
                List<String> refColumns = consColumns.get(refKey);
                if (refTable != null) {
                    fk.setReferencedSchema(refTable[0]);
                    fk.setReferencedTable(refTable[1]);
                } else {
                    // 参照先が対象スキーマ外の場合は個別に引く
                    List<Map<String, Object>> ref = rowSource.query(
                            "SELECT owner, table_name, column_name FROM all_cons_columns"
                                    + " WHERE owner = ? AND constraint_name = ? ORDER BY position",
                            str(con, "r_owner"), str(con, "r_constraint_name"));
                    if (!ref.isEmpty()) {
                        fk.setReferencedSchema(str(ref.get(0), "owner"));
                        fk.setReferencedTable(str(ref.get(0), "table_name"));
                        refColumns = new ArrayList<String>();
                        for (Map<String, Object> r : ref) {
                            refColumns.add(str(r, "column_name"));
                        }
                    }
                }
                if (refColumns != null) {
                    fk.setReferencedColumns(refColumns);
                }
                table.getForeignKeys().add(fk);
            }
        }
    }

    private void extractIndexes(DatabaseMetadata db, String owners) {
        Map<String, List<String>> indexColumns = new HashMap<String, List<String>>();
        for (Map<String, Object> ic : rowSource.query(
                "SELECT index_owner, index_name, column_name FROM all_ind_columns"
                        + " WHERE index_owner IN (" + owners + ")"
                        + " ORDER BY index_owner, index_name, column_position")) {
            String key = str(ic, "index_owner") + "|" + str(ic, "index_name");
            List<String> cols = indexColumns.get(key);
            if (cols == null) {
                cols = new ArrayList<String>();
                indexColumns.put(key, cols);
            }
            cols.add(str(ic, "column_name"));
        }
        for (Map<String, Object> ix : rowSource.query(
                "SELECT owner, index_name, table_owner, table_name, uniqueness"
                        + " FROM all_indexes WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, index_name")) {
            TableMetadata table = findTable(db, str(ix, "table_owner"), str(ix, "table_name"));
            if (table == null) {
                continue;
            }
            List<String> cols = indexColumns.get(str(ix, "owner") + "|" + str(ix, "index_name"));
            table.getIndexes().add(new IndexMetadata(str(ix, "index_name"),
                    "UNIQUE".equalsIgnoreCase(str(ix, "uniqueness")),
                    cols == null ? new ArrayList<String>() : cols));
        }
    }

    private void extractComments(DatabaseMetadata db, String owners) {
        for (Map<String, Object> tc : rowSource.query(
                "SELECT owner, table_name, comments FROM all_tab_comments"
                        + " WHERE owner IN (" + owners + ") AND comments IS NOT NULL")) {
            TableMetadata table = findTable(db, str(tc, "owner"), str(tc, "table_name"));
            if (table != null) {
                table.setComment(str(tc, "comments"));
            }
        }
        for (Map<String, Object> cc : rowSource.query(
                "SELECT owner, table_name, column_name, comments FROM all_col_comments"
                        + " WHERE owner IN (" + owners + ") AND comments IS NOT NULL")) {
            TableMetadata table = findTable(db, str(cc, "owner"), str(cc, "table_name"));
            if (table != null) {
                ColumnMetadata col = table.findColumn(str(cc, "column_name"));
                if (col != null) {
                    col.setComment(str(cc, "comments"));
                }
            }
        }
    }

    private void extractViews(DatabaseMetadata db, String owners) {
        for (Map<String, Object> v : rowSource.query(
                "SELECT owner, view_name, text FROM all_views WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, view_name")) {
            db.findOrCreateSchema(str(v, "owner")).getViews()
                    .add(new ViewMetadata(str(v, "view_name"), str(v, "text")));
        }
    }

    private void extractMaterializedViews(DatabaseMetadata db, String owners) {
        for (Map<String, Object> mv : rowSource.query(
                "SELECT owner, mview_name, query FROM all_mviews WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, mview_name")) {
            db.findOrCreateSchema(str(mv, "owner")).getMaterializedViews()
                    .add(new MaterializedViewMetadata(str(mv, "mview_name"), str(mv, "query")));
        }
    }

    private void extractTriggers(DatabaseMetadata db, String owners) {
        for (Map<String, Object> t : rowSource.query(
                "SELECT owner, trigger_name, trigger_type, triggering_event, table_name,"
                        + " trigger_body FROM all_triggers WHERE owner IN (" + owners + ")"
                        + " ORDER BY owner, trigger_name")) {
            TriggerMetadata trigger = new TriggerMetadata(str(t, "trigger_name"));
            String type = str(t, "trigger_type");
            if (type != null) {
                String upper = type.toUpperCase();
                if (upper.startsWith("BEFORE")) {
                    trigger.setTiming("BEFORE");
                } else if (upper.startsWith("AFTER")) {
                    trigger.setTiming("AFTER");
                } else if (upper.startsWith("INSTEAD")) {
                    trigger.setTiming("INSTEAD OF");
                }
                trigger.setLevel(upper.contains("EACH ROW") ? "ROW" : "STATEMENT");
            }
            String events = str(t, "triggering_event");
            if (events != null) {
                for (String e : events.toUpperCase().split("\\s+OR\\s+")) {
                    trigger.getEvents().add(e.trim());
                }
            }
            trigger.setTargetTable(str(t, "table_name"));
            trigger.setBody(str(t, "trigger_body"));
            db.findOrCreateSchema(str(t, "owner")).getTriggers().add(trigger);
        }
    }

    private void extractRoutines(DatabaseMetadata db, String owners) {
        for (Map<String, Object> o : rowSource.query(
                "SELECT owner, object_name, object_type FROM all_objects"
                        + " WHERE owner IN (" + owners + ")"
                        + " AND object_type IN ('PROCEDURE', 'FUNCTION')"
                        + " ORDER BY owner, object_name")) {
            String owner = str(o, "owner");
            String name = str(o, "object_name");
            RoutineMetadata routine = new RoutineMetadata(name, str(o, "object_type"));

            for (Map<String, Object> a : rowSource.query(
                    "SELECT argument_name, position, in_out, data_type FROM all_arguments"
                            + " WHERE owner = ? AND object_name = ? AND data_level = 0"
                            + " ORDER BY position",
                    owner, name)) {
                if (intOf(a, "position") != null && intOf(a, "position") == 0) {
                    routine.setReturnType(str(a, "data_type"));
                    continue;
                }
                String direction = str(a, "in_out");
                routine.getParameters().add(new ParameterMetadata(
                        str(a, "argument_name"),
                        direction == null ? "IN" : direction.replace("IN/OUT", "INOUT"),
                        str(a, "data_type")));
            }
            StringBuilder source = new StringBuilder();
            for (Map<String, Object> s : rowSource.query(
                    "SELECT text FROM all_source WHERE owner = ? AND name = ?"
                            + " AND type = ? ORDER BY line",
                    owner, name, routine.getRoutineType())) {
                source.append(str(s, "text"));
            }
            if (source.length() > 0) {
                routine.setSourceText(source.toString());
            }
            db.findOrCreateSchema(owner).getRoutines().add(routine);
        }
    }

    private void extractSequences(DatabaseMetadata db, String owners) {
        for (Map<String, Object> s : rowSource.query(
                "SELECT sequence_owner, sequence_name, min_value, max_value, increment_by,"
                        + " cycle_flag FROM all_sequences WHERE sequence_owner IN (" + owners + ")"
                        + " ORDER BY sequence_owner, sequence_name")) {
            SequenceMetadata seq = new SequenceMetadata(str(s, "sequence_name"));
            seq.setMinValue(longOf(s, "min_value"));
            seq.setMaxValue(longOf(s, "max_value"));
            seq.setIncrementBy(longOf(s, "increment_by"));
            seq.setCycle(bool(s, "cycle_flag", "Y"));
            db.findOrCreateSchema(str(s, "sequence_owner")).getSequences().add(seq);
        }
    }

    private void extractSynonyms(DatabaseMetadata db, String owners) {
        for (Map<String, Object> s : rowSource.query(
                "SELECT owner, synonym_name, table_owner, table_name FROM all_synonyms"
                        + " WHERE owner IN (" + owners + ") ORDER BY owner, synonym_name")) {
            db.findOrCreateSchema(str(s, "owner")).getSynonyms().add(new SynonymMetadata(
                    str(s, "synonym_name"), str(s, "table_owner"), str(s, "table_name")));
        }
    }

    private void extractPackages(DatabaseMetadata db, String owners) {
        for (Map<String, Object> p : rowSource.query(
                "SELECT DISTINCT owner, name FROM all_source WHERE owner IN (" + owners + ")"
                        + " AND type = 'PACKAGE' ORDER BY owner, name")) {
            String owner = str(p, "owner");
            String name = str(p, "name");
            PackageMetadata pkg = new PackageMetadata(name);
            pkg.setSpecSource(collectSource(owner, name, "PACKAGE"));
            pkg.setBodySource(collectSource(owner, name, "PACKAGE BODY"));
            db.findOrCreateSchema(owner).getPackages().add(pkg);
        }
    }

    private String collectSource(String owner, String name, String type) {
        StringBuilder source = new StringBuilder();
        for (Map<String, Object> s : rowSource.query(
                "SELECT text FROM all_source WHERE owner = ? AND name = ? AND type = ?"
                        + " ORDER BY line",
                owner, name, type)) {
            source.append(str(s, "text"));
        }
        return source.length() == 0 ? null : source.toString();
    }

    private void extractOthers(DatabaseMetadata db, String owners) {
        StringBuilder excluded = new StringBuilder();
        for (String t : STRUCTURED_TYPES) {
            if (excluded.length() > 0) {
                excluded.append(", ");
            }
            excluded.append('\'').append(t).append('\'');
        }
        for (Map<String, Object> o : rowSource.query(
                "SELECT owner, object_name, object_type FROM all_objects"
                        + " WHERE owner IN (" + owners + ")"
                        + " AND object_type NOT IN (" + excluded + ")"
                        + " ORDER BY owner, object_type, object_name")) {
            db.findOrCreateSchema(str(o, "owner")).getOthers().add(new GenericObjectMetadata(
                    str(o, "object_name"), str(o, "object_type"), null));
        }
    }

    private TableMetadata findTable(DatabaseMetadata db, String owner, String tableName) {
        SchemaMetadata schema = db.findSchema(owner);
        return schema == null ? null : schema.findTable(tableName);
    }
}
