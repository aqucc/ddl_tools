package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * プロシージャ/ファンクションの引数のメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParameterMetadata {

    private String name;
    /** IN / OUT / INOUT */
    private String direction;
    private String typeName;

    public ParameterMetadata() {
    }

    public ParameterMetadata(String name, String direction, String typeName) {
        this.name = name;
        this.direction = direction;
        this.typeName = typeName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }
}
