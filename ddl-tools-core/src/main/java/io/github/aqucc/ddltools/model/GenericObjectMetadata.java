package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 個別クラスを持たないその他すべてのオブジェクト種別(TYPE, DOMAIN, EXTENSION, DB LINK等)の
 * 汎用メタ情報。DDL原文を保持し情報を失わない。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericObjectMetadata {

    private String name;
    /** オブジェクト種別 (例: TYPE, DOMAIN, EXTENSION, DATABASE LINK) */
    private String objectType;
    private String ddlText;

    public GenericObjectMetadata() {
    }

    public GenericObjectMetadata(String name, String objectType, String ddlText) {
        this.name = name;
        this.objectType = objectType;
        this.ddlText = ddlText;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getDdlText() {
        return ddlText;
    }

    public void setDdlText(String ddlText) {
        this.ddlText = ddlText;
    }
}
