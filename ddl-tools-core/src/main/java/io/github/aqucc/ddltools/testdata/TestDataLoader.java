package io.github.aqucc.ddltools.testdata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * テストコードのデータ準備フェーズで使うJDBCヘルパー。
 * {@link JavaSnippetWriter} が生成するコードから呼び出される。
 */
public final class TestDataLoader {

    private TestDataLoader() {
    }

    /**
     * 1行INSERTする。
     *
     * @param conn    JDBC接続
     * @param table   テーブル名 (スキーマ修飾可)
     * @param columns カラム名配列
     * @param values  フィールド値配列 (columnsと同じ長さ)
     * @return 挿入行数 (常に1)
     */
    public static int insertRow(Connection conn, String table, String[] columns, Object[] values)
            throws SQLException {
        if (columns.length != values.length) {
            throw new IllegalArgumentException(
                    "columns.length != values.length: " + columns.length + " vs " + values.length);
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append(" (");
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
                placeholders.append(", ");
            }
            sql.append(columns[i]);
            placeholders.append('?');
        }
        sql.append(") VALUES (").append(placeholders).append(')');

        PreparedStatement ps = conn.prepareStatement(sql.toString());
        try {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, toJdbcValue(values[i]));
            }
            return ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    /** 古いJDBCドライバでも扱えるようjava.timeをjava.sql型へ変換する。 */
    private static Object toJdbcValue(Object value) {
        if (value instanceof LocalDate) {
            return java.sql.Date.valueOf((LocalDate) value);
        }
        if (value instanceof LocalDateTime) {
            return java.sql.Timestamp.valueOf((LocalDateTime) value);
        }
        if (value instanceof LocalTime) {
            return java.sql.Time.valueOf((LocalTime) value);
        }
        return value;
    }
}
