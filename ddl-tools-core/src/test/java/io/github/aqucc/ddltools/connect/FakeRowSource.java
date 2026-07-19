package io.github.aqucc.ddltools.connect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * テスト用の{@link RowSource}実装。
 * SQLに含まれる部分文字列(登録順に評価)でマッチした行データを返す。
 */
public class FakeRowSource implements RowSource {

    private static class Entry {
        final String sqlSubstring;
        final List<Map<String, Object>> rows;
        Object[] requiredParams;

        Entry(String sqlSubstring, List<Map<String, Object>> rows) {
            this.sqlSubstring = sqlSubstring;
            this.rows = rows;
        }
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<String> executedStatements = new ArrayList<String>();

    /**
     * SQL部分文字列に対する結果行を登録する。行は {カラム名, 値, カラム名, 値, ...} の
     * 可変長引数で1行ずつ追加する。
     */
    public FakeRowSource on(String sqlSubstring) {
        entries.add(new Entry(sqlSubstring, new ArrayList<Map<String, Object>>()));
        return this;
    }

    /**
     * 直前に登録した on(...) にバインドパラメータ条件を追加する。
     * 指定した値がすべて実際のパラメータに含まれる場合のみマッチする。
     */
    public FakeRowSource whenParams(Object... requiredParams) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("call on(sqlSubstring) first");
        }
        entries.get(entries.size() - 1).requiredParams = requiredParams;
        return this;
    }

    /** 直前に登録した on(...) に行を追加する。 */
    public FakeRowSource row(Object... keyValues) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("call on(sqlSubstring) first");
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            row.put(keyValues[i].toString().toLowerCase(Locale.ROOT), keyValues[i + 1]);
        }
        entries.get(entries.size() - 1).rows.add(row);
        return this;
    }

    @Override
    public List<Map<String, Object>> query(String sql, Object... params) {
        String normalized = sql.replaceAll("\\s+", " ");
        for (Entry entry : entries) {
            if (normalized.contains(entry.sqlSubstring) && paramsMatch(entry, params)) {
                return entry.rows;
            }
        }
        return new ArrayList<Map<String, Object>>();
    }

    private static boolean paramsMatch(Entry entry, Object[] actual) {
        if (entry.requiredParams == null) {
            return true;
        }
        for (Object required : entry.requiredParams) {
            boolean found = false;
            for (Object a : actual) {
                if (required.equals(a)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void execute(String sql) {
        executedStatements.add(sql);
    }

    public List<String> getExecutedStatements() {
        return executedStatements;
    }
}
