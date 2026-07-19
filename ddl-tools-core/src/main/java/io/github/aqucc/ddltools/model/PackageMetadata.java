package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * パッケージのメタ情報(Oracle)。仕様部/本体部のソースを保持する。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PackageMetadata {

    private String name;
    private String specSource;
    private String bodySource;

    public PackageMetadata() {
    }

    public PackageMetadata(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecSource() {
        return specSource;
    }

    public void setSpecSource(String specSource) {
        this.specSource = specSource;
    }

    public String getBodySource() {
        return bodySource;
    }

    public void setBodySource(String bodySource) {
        this.bodySource = bodySource;
    }
}
