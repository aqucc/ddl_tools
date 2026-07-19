package io.github.aqucc.ddltools.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * トリガーのメタ情報。本体はソーステキストのまま保持する。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriggerMetadata {

    private String name;
    /** BEFORE / AFTER / INSTEAD OF */
    private String timing;
    /** INSERT / UPDATE / DELETE / TRUNCATE */
    private List<String> events = new ArrayList<String>();
    private String targetTable;
    /** ROW / STATEMENT */
    private String level;
    /** トリガー定義全体のソース */
    private String body;

    public TriggerMetadata() {
    }

    public TriggerMetadata(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTiming() {
        return timing;
    }

    public void setTiming(String timing) {
        this.timing = timing;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
