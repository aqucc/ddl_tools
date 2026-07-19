package io.github.aqucc.ddltools.testdata;

import java.util.ArrayList;
import java.util.List;

import io.github.aqucc.ddltools.model.ColumnMetadata;

/**
 * 1テーブル分の生成済みテストデータ。
 * テーブル名・カラム名配列・行ごとの値配列を保持する。
 */
public class TableData {

    private final String schemaName;
    private final String tableName;
    private final List<ColumnMetadata> columns;
    private final List<List<Object>> rows = new ArrayList<List<Object>>();

    public TableData(String schemaName, String tableName, List<ColumnMetadata> columns) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.columns = columns;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    /** スキーマ修飾付きテーブル名。スキーマが擬似名(DEFAULT)の場合は修飾しない。 */
    public String getQualifiedName() {
        if (schemaName == null || schemaName.isEmpty() || "DEFAULT".equals(schemaName)) {
            return tableName;
        }
        return schemaName + "." + tableName;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public String[] getColumnNames() {
        String[] names = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            names[i] = columns.get(i).getName();
        }
        return names;
    }

    public List<List<Object>> getRows() {
        return rows;
    }
}
