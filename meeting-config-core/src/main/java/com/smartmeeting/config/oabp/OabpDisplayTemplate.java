package com.smartmeeting.config.oabp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会序 oabp SQL 结果集展示模板；为空或未配置 columns 时视为未启用（直通 raw sheet）。
 */
@Data
public class OabpDisplayTemplate {
    private Integer version = 1;
    /** table | grouped_table */
    private String displayMode;
    private String sheetName;
    private List<OabpDisplayColumn> columns = new ArrayList<>();
    private OabpDisplayContent content;
    private List<OabpDisplaySort> sort = new ArrayList<>();
    private OabpDisplayGroupBy groupBy;
    private List<OabpDisplayTotal> totals = new ArrayList<>();

    @JsonIgnore
    public boolean isEmpty() {
        return columns == null || columns.isEmpty();
    }
}
