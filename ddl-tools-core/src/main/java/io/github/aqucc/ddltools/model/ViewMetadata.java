package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ビューのメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViewMetadata {

    private String name;
    private List<String> columns = new ArrayList<String>();
    /** ビュー定義のSELECT文 */
    private String definitionSql;
    private String comment;

    public ViewMetadata() {
    }

    public ViewMetadata(String name, String definitionSql) {
        this.name = name;
        this.definitionSql = definitionSql;
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

    public String getDefinitionSql() {
        return definitionSql;
    }

    public void setDefinitionSql(String definitionSql) {
        this.definitionSql = definitionSql;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
