package com.smartmeeting.config.oabp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 展示模板列定义。 */
@Data
public class OabpDisplayColumn {
    private String source;
    private String label;
    private Boolean visible;
    private Integer width;
    private String align;
    /** plain / date / datetime / percent / number / enum */
    private String format;
    /** plain / badge / progress_bar / long_text / numeric */
    private String renderAs;
    private Map<String, String> map = new LinkedHashMap<>();
    private String defaultValue;

    public boolean isVisible() {
        return visible == null || visible;
    }
}
