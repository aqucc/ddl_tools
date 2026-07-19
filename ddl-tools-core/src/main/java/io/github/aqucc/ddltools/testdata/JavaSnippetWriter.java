package io.github.aqucc.ddltools.testdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * テストコードのデータ準備フェーズで使う、
 * 「テーブル名・カラム名配列・値配列」の3引数列挙形式のJavaコード断片を生成する。
 *
 * <p>生成されるコードは {@link TestDataLoader#insertRow} と組で使用する。
 */
public class JavaSnippetWriter {

    /** 3引数呼び出しの列挙を含む完全なJavaクラスのソースを生成する。 */
    public String writeClass(TestDataSet dataSet, String packageName, String className) {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("import java.sql.Connection;\n");
        sb.append("import java.sql.SQLException;\n\n");
        sb.append("import io.github.aqucc.ddltools.testdata.TestDataLoader;\n\n");
        sb.append("/** ddl-tools が生成したテストデータ投入コード。 */\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    public static void load(Connection conn) throws SQLException {\n");
        sb.append(writeCalls(dataSet, "        "));
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** insertRow(テーブル名, カラム名配列, 値配列) 呼び出しの列挙のみを生成する。 */
    public String writeCalls(TestDataSet dataSet, String indent) {
        StringBuilder sb = new StringBuilder();
        for (TableData table : dataSet.getTables()) {
            sb.append(indent).append("// ").append(table.getQualifiedName()).append('\n');
            String columnArray = columnArrayLiteral(table.getColumnNames());
            for (List<Object> row : table.getRows()) {
                sb.append(indent)
                        .append("TestDataLoader.insertRow(conn, \"")
                        .append(table.getQualifiedName()).append("\",\n")
                        .append(indent).append("        ").append(columnArray).append(",\n")
                        .append(indent).append("        ").append(valueArrayLiteral(row))
                        .append(");\n");
            }
        }
        return sb.toString();
    }

    private String columnArrayLiteral(String[] columns) {
        StringBuilder sb = new StringBuilder("new String[] {");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(columns[i]).append('"');
        }
        return sb.append('}').toString();
    }

    private String valueArrayLiteral(List<Object> row) {
        StringBuilder sb = new StringBuilder("new Object[] {");
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(javaLiteral(row.get(i)));
        }
        return sb.append('}').toString();
    }

    public String javaLiteral(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return '"' + escapeJava((String) value) + '"';
        }
        if (value instanceof Long) {
            return value + "L";
        }
        if (value instanceof Integer) {
            return value.toString();
        }
        if (value instanceof BigDecimal) {
            return "new java.math.BigDecimal(\"" + value + "\")";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "Boolean.TRUE" : "Boolean.FALSE";
        }
        if (value instanceof LocalDate) {
            return "java.time.LocalDate.parse(\"" + value + "\")";
        }
        if (value instanceof LocalDateTime) {
            return "java.time.LocalDateTime.parse(\"" + value + "\")";
        }
        if (value instanceof LocalTime) {
            return "java.time.LocalTime.parse(\"" + value + "\")";
        }
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            StringBuilder sb = new StringBuilder("new byte[] {");
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(bytes[i]);
            }
            return sb.append('}').toString();
        }
        if (value instanceof java.util.UUID) {
            return "java.util.UUID.fromString(\"" + value + "\")";
        }
        return '"' + escapeJava(value.toString()) + '"';
    }

    private static String escapeJava(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
