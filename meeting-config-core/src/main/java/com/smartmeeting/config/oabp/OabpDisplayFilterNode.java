package com.smartmeeting.config.oabp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 条件筛选树节点：rule 或 group。 */
@Data
public class OabpDisplayFilterNode {
    /** rule | group */
    private String type;
    /** rule: 比较运算符；group: and | or | not */
    private String op;
    /** rule: 源列名 */
    private String field;
    /** rule: 比较值（字符串） */
    private String value;
    private List<OabpDisplayFilterNode> children = new ArrayList<>();
}
