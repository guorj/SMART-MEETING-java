package com.smartmeeting.matterprogress.config;

/**
 * 飞书电子表格拉取上限（可由 meeting-server {@code matter-progress.fetch.*} 注入）。
 */
public final class SpreadsheetFetchLimits {

    public static final SpreadsheetFetchLimits DEFAULT = new SpreadsheetFetchLimits(20, 200, 26, 500, 50);

    private final int maxSheets;
    private final int defaultMaxRows;
    private final int defaultMaxCols;
    private final int absMaxRows;
    private final int absMaxCols;

    public SpreadsheetFetchLimits(int maxSheets, int defaultMaxRows, int defaultMaxCols,
                                  int absMaxRows, int absMaxCols) {
        this.maxSheets = maxSheets;
        this.defaultMaxRows = defaultMaxRows;
        this.defaultMaxCols = defaultMaxCols;
        this.absMaxRows = absMaxRows;
        this.absMaxCols = absMaxCols;
    }

    public int maxSheets() {
        return maxSheets;
    }

    public int defaultMaxRows() {
        return defaultMaxRows;
    }

    public int defaultMaxCols() {
        return defaultMaxCols;
    }

    public int absMaxRows() {
        return absMaxRows;
    }

    public int absMaxCols() {
        return absMaxCols;
    }
}
