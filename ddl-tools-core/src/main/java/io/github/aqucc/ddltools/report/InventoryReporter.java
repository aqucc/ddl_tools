package io.github.aqucc.ddltools.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.GenericObjectMetadata;
import io.github.aqucc.ddltools.model.IndexMetadata;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.PackageMetadata;
import io.github.aqucc.ddltools.model.RoutineMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.SequenceMetadata;
import io.github.aqucc.ddltools.model.SynonymMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.TriggerMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;

/**
 * メタ情報から全スキーマ×全オブジェクト種別の一覧を生成する。
 */
public class InventoryReporter {

    public enum Format {
        TEXT, CSV, JSON
    }

    /** 一覧の1行。 */
    public static class Row {
        private final String schema;
        private final String objectType;
        private final String name;
        private final String detail;

        public Row(String schema, String objectType, String name, String detail) {
            this.schema = schema;
            this.objectType = objectType;
            this.name = name;
            this.detail = detail;
        }

        public String getSchema() {
            return schema;
        }

        public String getObjectType() {
            return objectType;
        }

        public String getName() {
            return name;
        }

        public String getDetail() {
            return detail;
        }
    }

    public List<Row> collectRows(DatabaseMetadata db) {
        List<Row> rows = new ArrayList<Row>();
        for (SchemaMetadata schema : db.getSchemas()) {
            String s = schema.getName();
            for (TableMetadata t : schema.getTables()) {
                rows.add(new Row(s, "TABLE", t.getName(), t.getColumns().size() + " columns"));
                for (IndexMetadata ix : t.getIndexes()) {
                    rows.add(new Row(s, "INDEX", ix.getName(),
                            (ix.isUnique() ? "UNIQUE " : "") + "ON " + t.getName()));
                }
            }
            for (ViewMetadata v : schema.getViews()) {
                rows.add(new Row(s, "VIEW", v.getName(), ""));
            }
            for (MaterializedViewMetadata mv : schema.getMaterializedViews()) {
                rows.add(new Row(s, "MATERIALIZED VIEW", mv.getName(), ""));
            }
            for (TriggerMetadata trg : schema.getTriggers()) {
                rows.add(new Row(s, "TRIGGER", trg.getName(),
                        trg.getTargetTable() == null ? "" : "ON " + trg.getTargetTable()));
            }
            for (RoutineMetadata r : schema.getRoutines()) {
                rows.add(new Row(s, r.getRoutineType(), r.getName(),
                        r.getParameters().size() + " args"
                                + (r.getReturnType() == null ? "" : " -> " + r.getReturnType())));
            }
            for (SequenceMetadata seq : schema.getSequences()) {
                rows.add(new Row(s, "SEQUENCE", seq.getName(), ""));
            }
            for (SynonymMetadata syn : schema.getSynonyms()) {
                String target = (syn.getTargetSchema() == null ? "" : syn.getTargetSchema() + ".")
                        + syn.getTargetName();
                rows.add(new Row(s, "SYNONYM", syn.getName(), "FOR " + target));
            }
            for (PackageMetadata pkg : schema.getPackages()) {
                String detail = (pkg.getSpecSource() != null ? "spec" : "")
                        + (pkg.getBodySource() != null ? (pkg.getSpecSource() != null ? "+body" : "body") : "");
                rows.add(new Row(s, "PACKAGE", pkg.getName(), detail));
            }
            for (GenericObjectMetadata o : schema.getOthers()) {
                rows.add(new Row(s, o.getObjectType(), o.getName(), ""));
            }
        }
        return rows;
    }

    public String report(DatabaseMetadata db, Format format) {
        List<Row> rows = collectRows(db);
        switch (format) {
            case CSV:
                return toCsv(rows);
            case JSON:
                return toJson(rows);
            case TEXT:
            default:
                return toText(rows);
        }
    }

    private String toText(List<Row> rows) {
        int wSchema = "SCHEMA".length();
        int wType = "TYPE".length();
        int wName = "NAME".length();
        for (Row r : rows) {
            wSchema = Math.max(wSchema, r.getSchema().length());
            wType = Math.max(wType, r.getObjectType().length());
            wName = Math.max(wName, r.getName().length());
        }
        StringBuilder sb = new StringBuilder();
        appendText(sb, "SCHEMA", "TYPE", "NAME", "DETAIL", wSchema, wType, wName);
        appendText(sb, repeat('-', wSchema), repeat('-', wType), repeat('-', wName), "------",
                wSchema, wType, wName);
        for (Row r : rows) {
            appendText(sb, r.getSchema(), r.getObjectType(), r.getName(), r.getDetail(),
                    wSchema, wType, wName);
        }
        sb.append('\n').append(summaryText(rows));
        return sb.toString();
    }

    private void appendText(StringBuilder sb, String schema, String type, String name,
            String detail, int wSchema, int wType, int wName) {
        sb.append(pad(schema, wSchema)).append("  ")
                .append(pad(type, wType)).append("  ")
                .append(pad(name, wName)).append("  ")
                .append(detail).append('\n');
    }

    private String summaryText(List<Row> rows) {
        Map<String, Integer> counts = countByType(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(rows.size()).append(" objects (");
        boolean first = true;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        sb.append(")\n");
        return sb.toString();
    }

    private String toCsv(List<Row> rows) {
        StringBuilder sb = new StringBuilder("schema,object_type,name,detail\n");
        for (Row r : rows) {
            sb.append(csv(r.getSchema())).append(',')
                    .append(csv(r.getObjectType())).append(',')
                    .append(csv(r.getName())).append(',')
                    .append(csv(r.getDetail())).append('\n');
        }
        return sb.toString();
    }

    private String toJson(List<Row> rows) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("objects", rows);
        root.put("summary", countByType(rows));
        root.put("total", rows.size());
        try {
            ObjectMapper om = new ObjectMapper();
            om.enable(SerializationFeature.INDENT_OUTPUT);
            return om.writeValueAsString(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Integer> countByType(List<Row> rows) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Row r : rows) {
            Integer c = counts.get(r.getObjectType());
            counts.put(r.getObjectType(), c == null ? 1 : c + 1);
        }
        return counts;
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
