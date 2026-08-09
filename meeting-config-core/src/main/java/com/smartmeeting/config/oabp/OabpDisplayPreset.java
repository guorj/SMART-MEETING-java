package com.smartmeeting.config.oabp;

import lombok.Builder;
import lombok.Value;

/** 内置展示模板预设（简洁模式一键应用）。 */
@Value
@Builder
public class OabpDisplayPreset {
    String id;
    String name;
    String description;
    OabpDisplayTemplate template;
}
