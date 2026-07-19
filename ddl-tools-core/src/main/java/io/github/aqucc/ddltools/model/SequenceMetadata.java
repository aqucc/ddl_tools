package io.github.aqucc.ddltools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * シーケンスのメタ情報。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceMetadata {

    private String name;
    private Long startValue;
    private Long incrementBy;
    private Long minValue;
    private Long maxValue;
    private Boolean cycle;

    public SequenceMetadata() {
    }

    public SequenceMetadata(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getStartValue() {
        return startValue;
    }

    public void setStartValue(Long startValue) {
        this.startValue = startValue;
    }

    public Long getIncrementBy() {
        return incrementBy;
    }

    public void setIncrementBy(Long incrementBy) {
        this.incrementBy = incrementBy;
    }

    public Long getMinValue() {
        return minValue;
    }

    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    public Boolean getCycle() {
        return cycle;
    }

    public void setCycle(Boolean cycle) {
        this.cycle = cycle;
    }
}
