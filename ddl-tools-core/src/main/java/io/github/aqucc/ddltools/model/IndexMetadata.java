package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * インデックスのメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexMetadata {

    private String name;
    private boolean unique;
    private List<String> columns = new ArrayList<String>();

    public IndexMetadata() {
    }

    public IndexMetadata(String name, boolean unique, List<String> columns) {
        this.name = name;
        this.unique = unique;
        this.columns = columns;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }
}
