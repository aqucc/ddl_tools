package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * プロシージャ/ファンクションのメタ情報。
 * シグネチャを構造化し、本体はソーステキストのまま保持する。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutineMetadata {

    public static final String TYPE_PROCEDURE = "PROCEDURE";
    public static final String TYPE_FUNCTION = "FUNCTION";

    private String name;
    /** PROCEDURE / FUNCTION */
    private String routineType;
    private List<ParameterMetadata> parameters = new ArrayList<ParameterMetadata>();
    private String returnType;
    /** 定義全体のソース */
    private String sourceText;

    public RoutineMetadata() {
    }

    public RoutineMetadata(String name, String routineType) {
        this.name = name;
        this.routineType = routineType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoutineType() {
        return routineType;
    }

    public void setRoutineType(String routineType) {
        this.routineType = routineType;
    }

    public List<ParameterMetadata> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterMetadata> parameters) {
        this.parameters = parameters;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }
}
