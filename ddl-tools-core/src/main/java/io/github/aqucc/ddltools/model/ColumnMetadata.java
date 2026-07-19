package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * カラムのメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColumnMetadata {

    private String name;
    /** 方言そのままの型名 (例: VARCHAR2, NUMBER, TEXT, TIMESTAMP(6)) */
    private String typeName;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private boolean nullable = true;
    private String defaultValue;
    /** IDENTITY列(Oracle 12c+/PG)やserial型など、DB側で値が採番される列ならtrue */
    private Boolean identity;
    private String comment;

    public ColumnMetadata() {
    }

    public ColumnMetadata(String name, String typeName) {
        this.name = name;
        this.typeName = typeName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getIdentity() {
        return identity;
    }

    public void setIdentity(Boolean identity) {
        this.identity = identity;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
