package com.smartmeeting.config.oabp;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class OabpDisplayGroupBy {
    private String source;
    private String label;
    private Map<String, String> map = new LinkedHashMap<>();
    private List<String> order = new ArrayList<>();
    private Boolean showCount;
}
