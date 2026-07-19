package io.github.aqucc.ddltools.testdata;

import java.util.ArrayList;
import java.util.List;

/**
 * 生成されたテストデータ一式。テーブルはFK依存を考慮したINSERT可能な順序で並ぶ。
 */
public class TestDataSet {

    private final List<TableData> tables = new ArrayList<TableData>();
    private final List<String> warnings = new ArrayList<String>();

    public List<TableData> getTables() {
        return tables;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public TableData findTable(String tableName) {
        for (TableData t : tables) {
            if (t.getTableName().equalsIgnoreCase(tableName)) {
                return t;
            }
        }
        return null;
    }
}
