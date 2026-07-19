package io.github.aqucc.ddltools.testdata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CHECK制約から導いたカラムの値域。
 */
public class ColumnDomain {

    /** IN (...) で許可された値のリスト (無ければ空) */
    private final List<Object> allowedValues = new ArrayList<Object>();
    private BigDecimal minValue;
    private BigDecimal maxValue;

    public List<Object> getAllowedValues() {
        return allowedValues;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public void setMinValue(BigDecimal minValue) {
        if (this.minValue == null || minValue.compareTo(this.minValue) > 0) {
            this.minValue = minValue;
        }
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(BigDecimal maxValue) {
        if (this.maxValue == null || maxValue.compareTo(this.maxValue) < 0) {
            this.maxValue = maxValue;
        }
    }
}
