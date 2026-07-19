package io.github.aqucc.ddltools.dump;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.aqucc.ddltools.connect.RowSource;
import io.github.aqucc.ddltools.connect.SqlIdentifiers;

/**
 * pg_dump外部コマンドを使わず、カタログ(pg_catalog)と pg_get_* 関数からDDLを組み立てる。
 */
public class PostgresDdlDumper implements DdlDumper {

    private final RowSource rowSource;

    public PostgresDdlDumper(RowSource rowSource) {
        this.rowSource = rowSource;
    }

    @Override
    public DumpResult dump(List<String> schemas) {
        List<String> targetSchemas = (schemas == null || schemas.isEmpty())
                ? Arrays.asList("public") : schemas;
        DumpResult result = new DumpResult();
        String in = SqlIdentifiers.inList(targetSchemas);

        dumpSchemas(targetSchemas, result);
        dumpSequences(in, result);
        dumpTables(in, result);
        dumpIndexes(in, result);
        dumpViews(in, result);
        dumpMaterializedViews(in, result);
        dumpRoutines(in, result);
        dumpTriggers(in, result);
        return result;
    }

    private void dumpSchemas(List<String> schemas, DumpResult result) {
        for (String schema : schemas) {
            try {
                result.getObjects().add(new DdlObject(schema, "SCHEMA", schema,
                        "CREATE SCHEMA IF NOT EXISTS " + schema + ";"));
            } catch (RuntimeException e) {
                result.getWarnings().add("failed to build SCHEMA ddl for " + schema
                        + ": " + e.getMessage());
            }
        }
    }

    private void dumpSequences(String in, DumpResult result) {
        try {
            for (Map<String, Object> s : rowSource.query(
                    "SELECT schemaname, sequencename, start_value, increment_by, min_value,"
                            + " max_value, cycle FROM pg_sequences"
                            + " WHERE schemaname IN (" + in + ")")) {
                String schema = string(s, "schemaname");
                String name = string(s, "sequencename");
                try {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE SEQUENCE ").append(schema).append('.').append(name)
                            .append(" START WITH ").append(string(s, "start_value"))
                            .append(" INCREMENT BY ").append(string(s, "increment_by"))
                            .append(" MINVALUE ").append(string(s, "min_value"))
                            .append(" MAXVALUE ").append(string(s, "max_value"))
                            .append(' ').append(isTrue(s.get("cycle")) ? "CYCLE" : "NO CYCLE")
                            .append(';');
                    result.getObjects().add(new DdlObject(schema, "SEQUENCE", name, ddl.toString()));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build SEQUENCE ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query sequences: " + e.getMessage());
        }
    }

    private void dumpTables(String in, DumpResult result) {
        List<Map<String, Object>> tables;
        try {
            tables = rowSource.query(
                    "SELECT n.nspname AS schema_name, c.relname AS table_name"
                            + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                            + " WHERE n.nspname IN (" + in + ") AND c.relkind IN ('r', 'p')"
                            + " ORDER BY n.nspname, c.relname");
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query tables: " + e.getMessage());
            return;
        }
        for (Map<String, Object> t : tables) {
            String schema = string(t, "schema_name");
            String name = string(t, "table_name");
            try {
                String ddl = buildTableDdl(schema, name);
                result.getObjects().add(new DdlObject(schema, "TABLE", name, ddl));
            } catch (RuntimeException e) {
                result.getWarnings().add("failed to build TABLE ddl for "
                        + schema + "." + name + ": " + e.getMessage());
            }
        }
    }

    private String buildTableDdl(String schema, String table) {
        List<Map<String, Object>> columns = rowSource.query(
                "SELECT a.attname AS column_name, format_type(a.atttypid, a.atttypmod) AS data_type,"
                        + " a.attnotnull AS not_null,"
                        + " pg_get_expr(d.adbin, d.adrelid) AS default_value"
                        + " FROM pg_attribute a"
                        + " JOIN pg_class c ON c.oid = a.attrelid"
                        + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum"
                        + " WHERE n.nspname = ? AND c.relname = ?"
                        + " AND a.attnum > 0 AND NOT a.attisdropped"
                        + " ORDER BY a.attnum",
                schema, table);

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(schema).append('.').append(table).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> col = columns.get(i);
            ddl.append("  ").append(string(col, "column_name")).append(' ')
                    .append(string(col, "data_type"));
            String defaultValue = string(col, "default_value");
            if (defaultValue != null) {
                ddl.append(" DEFAULT ").append(defaultValue);
            }
            if (isTrue(col.get("not_null"))) {
                ddl.append(" NOT NULL");
            }
            if (i < columns.size() - 1) {
                ddl.append(',');
            }
            ddl.append('\n');
        }
        ddl.append(");");

        List<Map<String, Object>> constraints = rowSource.query(
                "SELECT conname, pg_get_constraintdef(oid) AS condef FROM pg_constraint"
                        + " WHERE connamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?)"
                        + " AND conrelid = (SELECT oid FROM pg_class WHERE relname = ?"
                        + " AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?))"
                        + " ORDER BY conname",
                schema, table, schema);
        for (Map<String, Object> c : constraints) {
            ddl.append('\n').append("ALTER TABLE ").append(schema).append('.').append(table)
                    .append(" ADD CONSTRAINT ").append(string(c, "conname"))
                    .append(' ').append(string(c, "condef")).append(';');
        }
        return ddl.toString();
    }

    private void dumpIndexes(String in, DumpResult result) {
        try {
            for (Map<String, Object> ix : rowSource.query(
                    "SELECT n.nspname AS schema_name, i.relname AS index_name,"
                            + " pg_get_indexdef(x.indexrelid) AS indexdef"
                            + " FROM pg_index x"
                            + " JOIN pg_class i ON i.oid = x.indexrelid"
                            + " JOIN pg_class t ON t.oid = x.indrelid"
                            + " JOIN pg_namespace n ON n.oid = t.relnamespace"
                            + " WHERE n.nspname IN (" + in + ")"
                            + " AND NOT x.indisprimary"
                            + " AND NOT EXISTS (SELECT 1 FROM pg_constraint c"
                            + " WHERE c.conindid = i.oid)")) {
                String schema = string(ix, "schema_name");
                String name = string(ix, "index_name");
                try {
                    result.getObjects().add(new DdlObject(schema, "INDEX", name,
                            string(ix, "indexdef") + ";"));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build INDEX ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query indexes: " + e.getMessage());
        }
    }

    private void dumpViews(String in, DumpResult result) {
        try {
            for (Map<String, Object> v : rowSource.query(
                    "SELECT schemaname, viewname, definition FROM pg_views"
                            + " WHERE schemaname IN (" + in + ")")) {
                String schema = string(v, "schemaname");
                String name = string(v, "viewname");
                try {
                    String ddl = "CREATE OR REPLACE VIEW " + schema + '.' + name + " AS\n"
                            + ensureTrailingSemicolon(string(v, "definition"));
                    result.getObjects().add(new DdlObject(schema, "VIEW", name, ddl));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build VIEW ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query views: " + e.getMessage());
        }
    }

    private void dumpMaterializedViews(String in, DumpResult result) {
        try {
            for (Map<String, Object> mv : rowSource.query(
                    "SELECT schemaname, matviewname, definition FROM pg_matviews"
                            + " WHERE schemaname IN (" + in + ")")) {
                String schema = string(mv, "schemaname");
                String name = string(mv, "matviewname");
                try {
                    String ddl = "CREATE MATERIALIZED VIEW " + schema + '.' + name + " AS\n"
                            + ensureTrailingSemicolon(string(mv, "definition"));
                    result.getObjects().add(new DdlObject(schema, "MATERIALIZED VIEW", name, ddl));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build MATERIALIZED VIEW ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query materialized views: " + e.getMessage());
        }
    }

    private void dumpRoutines(String in, DumpResult result) {
        try {
            for (Map<String, Object> r : rowSource.query(
                    "SELECT n.nspname AS schema_name, p.proname AS routine_name,"
                            + " CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END"
                            + " AS routine_type, pg_get_functiondef(p.oid) AS definition"
                            + " FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace"
                            + " WHERE n.nspname IN (" + in + ") AND p.prokind IN ('f', 'p')")) {
                String schema = string(r, "schema_name");
                String name = string(r, "routine_name");
                String type = string(r, "routine_type");
                try {
                    result.getObjects().add(new DdlObject(schema, type, name,
                            string(r, "definition") + ";"));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build " + type + " ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query functions/procedures: " + e.getMessage());
        }
    }

    private void dumpTriggers(String in, DumpResult result) {
        try {
            for (Map<String, Object> t : rowSource.query(
                    "SELECT n.nspname AS schema_name, t.tgname AS trigger_name,"
                            + " pg_get_triggerdef(t.oid) AS triggerdef"
                            + " FROM pg_trigger t"
                            + " JOIN pg_class c ON c.oid = t.tgrelid"
                            + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                            + " WHERE n.nspname IN (" + in + ") AND NOT t.tgisinternal")) {
                String schema = string(t, "schema_name");
                String name = string(t, "trigger_name");
                try {
                    result.getObjects().add(new DdlObject(schema, "TRIGGER", name,
                            string(t, "triggerdef") + ";"));
                } catch (RuntimeException e) {
                    result.getWarnings().add("failed to build TRIGGER ddl for "
                            + schema + "." + name + ": " + e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            result.getWarnings().add("failed to query triggers: " + e.getMessage());
        }
    }

    private static String ensureTrailingSemicolon(String sql) {
        if (sql == null) {
            return ";";
        }
        String s = sql.trim();
        return s.endsWith(";") ? s : s + ";";
    }

    /** Boolean または "t"/"true" 文字列表現の両対応で真偽を判定する。 */
    private static boolean isTrue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String s = value.toString();
        return "t".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s);
    }

    private static String string(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }
}
