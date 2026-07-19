package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * シノニムのメタ情報(Oracle)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SynonymMetadata {

    private String name;
    private String targetSchema;
    private String targetName;

    public SynonymMetadata() {
    }

    public SynonymMetadata(String name, String targetSchema, String targetName) {
        this.name = name;
        this.targetSchema = targetSchema;
        this.targetName = targetName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetSchema() {
        return targetSchema;
    }

    public void setTargetSchema(String targetSchema) {
        this.targetSchema = targetSchema;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }
}
