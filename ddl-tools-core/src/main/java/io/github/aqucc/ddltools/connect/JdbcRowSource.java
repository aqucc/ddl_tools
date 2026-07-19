package io.github.aqucc.ddltools.connect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC接続に対する{@link RowSource}実装。
 */
public class JdbcRowSource implements RowSource {

    private final Connection connection;

    public JdbcRowSource(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Map<String, Object>> query(String sql, Object... params) {
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            try {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ResultSet rs = ps.executeQuery();
                try {
                    return toRows(rs);
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }

    @Override
    public void execute(String sql) {
        try {
            Statement st = connection.createStatement();
            try {
                st.execute(sql);
            } finally {
                st.close();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("execute failed: " + sql, e);
        }
    }

    private static List<Map<String, Object>> toRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        String[] names = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            names[i] = meta.getColumnLabel(i + 1).toLowerCase(Locale.ROOT);
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (int i = 0; i < columnCount; i++) {
                Object value = rs.getObject(i + 1);
                // Oracle LONG型 (all_views.text など) はgetStringでのみ取得できる場合がある
                if (value == null) {
                    String s = rs.getString(i + 1);
                    value = s;
                }
                row.put(names[i], value);
            }
            rows.add(row);
        }
        return rows;
    }
}
