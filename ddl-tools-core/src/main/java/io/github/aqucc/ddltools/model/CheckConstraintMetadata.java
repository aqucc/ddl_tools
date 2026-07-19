package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CHECK制約のメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckConstraintMetadata {

    private String name;
    private String expression;

    public CheckConstraintMetadata() {
    }

    public CheckConstraintMetadata(String name, String expression) {
        this.name = name;
        this.expression = expression;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }
}
