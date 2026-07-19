package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * テーブルのメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableMetadata {

    private String name;
    private String comment;
    private List<ColumnMetadata> columns = new ArrayList<ColumnMetadata>();
    private PrimaryKeyMetadata primaryKey;
    private List<ForeignKeyMetadata> foreignKeys = new ArrayList<ForeignKeyMetadata>();
    private List<UniqueConstraintMetadata> uniqueConstraints = new ArrayList<UniqueConstraintMetadata>();
    private List<CheckConstraintMetadata> checkConstraints = new ArrayList<CheckConstraintMetadata>();
    private List<IndexMetadata> indexes = new ArrayList<IndexMetadata>();

    public TableMetadata() {
    }

    public TableMetadata(String name) {
        this.name = name;
    }

    /** カラム名で検索(存在しなければnull)。大文字小文字は区別しない。 */
    public ColumnMetadata findColumn(String columnName) {
        for (ColumnMetadata c : columns) {
            if (c.getName().equalsIgnoreCase(columnName)) {
                return c;
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMetadata> columns) {
        this.columns = columns;
    }

    public PrimaryKeyMetadata getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(PrimaryKeyMetadata primaryKey) {
        this.primaryKey = primaryKey;
    }

    public List<ForeignKeyMetadata> getForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(List<ForeignKeyMetadata> foreignKeys) {
        this.foreignKeys = foreignKeys;
    }

    public List<UniqueConstraintMetadata> getUniqueConstraints() {
        return uniqueConstraints;
    }

    public void setUniqueConstraints(List<UniqueConstraintMetadata> uniqueConstraints) {
        this.uniqueConstraints = uniqueConstraints;
    }

    public List<CheckConstraintMetadata> getCheckConstraints() {
        return checkConstraints;
    }

    public void setCheckConstraints(List<CheckConstraintMetadata> checkConstraints) {
        this.checkConstraints = checkConstraints;
    }

    public List<IndexMetadata> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexMetadata> indexes) {
        this.indexes = indexes;
    }
}
