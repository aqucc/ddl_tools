package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 主キー制約のメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrimaryKeyMetadata {

    private String name;
    private List<String> columns = new ArrayList<String>();

    public PrimaryKeyMetadata() {
    }

    public PrimaryKeyMetadata(String name, List<String> columns) {
        this.name = name;
        this.columns = columns;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }
}
