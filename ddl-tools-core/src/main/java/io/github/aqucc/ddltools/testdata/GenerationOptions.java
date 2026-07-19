package io.github.aqucc.ddltools.testdata;

/**
 * テストデータ生成のオプション。
 */
public class GenerationOptions {

    private int rows = 10;
    private long seed = 42L;
    /** NULL可カラムにNULLを混ぜる割合 (0.0〜1.0) */
    private double nullRatio = 0.2;
    /** ビュー定義を解析し、結合条件・固定条件を考慮した値を生成する */
    private boolean considerViews = true;
    private String baseDate = "2024-01-01";

    public int getRows() {
        return rows;
    }

    public GenerationOptions setRows(int rows) {
        this.rows = rows;
        return this;
    }

    public long getSeed() {
        return seed;
    }

    public GenerationOptions setSeed(long seed) {
        this.seed = seed;
        return this;
    }

    public double getNullRatio() {
        return nullRatio;
    }

    public GenerationOptions setNullRatio(double nullRatio) {
        this.nullRatio = nullRatio;
        return this;
    }

    public boolean isConsiderViews() {
        return considerViews;
    }

    public GenerationOptions setConsiderViews(boolean considerViews) {
        this.considerViews = considerViews;
        return this;
    }

    public String getBaseDate() {
        return baseDate;
    }

    public GenerationOptions setBaseDate(String baseDate) {
        this.baseDate = baseDate;
        return this;
    }
}
