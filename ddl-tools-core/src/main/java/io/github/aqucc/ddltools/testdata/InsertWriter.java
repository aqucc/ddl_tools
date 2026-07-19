package io.github.aqucc.ddltools.testdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.github.aqucc.ddltools.model.Dialect;

/**
 * テストデータを実行可能なINSERT文として書き出す。リテラルは方言に合わせて整形する。
 */
public class InsertWriter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Dialect dialect;

    public InsertWriter(Dialect dialect) {
        this.dialect = dialect;
    }

    /** 全テーブル分のINSERT文を1つのスクリプトとして書き出す。 */
    public String writeAll(TestDataSet dataSet) {
        StringBuilder sb = new StringBuilder();
        for (TableData table : dataSet.getTables()) {
            sb.append("-- ").append(table.getQualifiedName()).append('\n');
            sb.append(writeTable(table));
            sb.append('\n');
        }
        return sb.toString();
    }

    public String writeTable(TableData table) {
        StringBuilder sb = new StringBuilder();
        String columnList = join(table.getColumnNames());
        for (List<Object> row : table.getRows()) {
            sb.append("INSERT INTO ").append(table.getQualifiedName())
                    .append(" (").append(columnList).append(") VALUES (");
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(formatValue(row.get(i)));
            }
            sb.append(");\n");
        }
        return sb.toString();
    }

    public String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        if (value instanceof Long || value instanceof Integer || value instanceof BigDecimal) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            if (dialect == Dialect.ORACLE) {
                return ((Boolean) value) ? "1" : "0";
            }
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        if (value instanceof LocalDate) {
            String s = ((LocalDate) value).format(DATE_FMT);
            return dialect == Dialect.ORACLE
                    ? "TO_DATE('" + s + "', 'YYYY-MM-DD')"
                    : "DATE '" + s + "'";
        }
        if (value instanceof LocalDateTime) {
            String s = ((LocalDateTime) value).format(TS_FMT);
            return dialect == Dialect.ORACLE
                    ? "TO_TIMESTAMP('" + s + "', 'YYYY-MM-DD HH24:MI:SS')"
                    : "TIMESTAMP '" + s + "'";
        }
        if (value instanceof LocalTime) {
            String s = ((LocalTime) value).format(TIME_FMT);
            return dialect == Dialect.ORACLE ? "'" + s + "'" : "TIME '" + s + "'";
        }
        if (value instanceof byte[]) {
            String hex = toHex((byte[]) value);
            return dialect == Dialect.ORACLE
                    ? "HEXTORAW('" + hex + "')"
                    : "'\\x" + hex + "'";
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String join(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
