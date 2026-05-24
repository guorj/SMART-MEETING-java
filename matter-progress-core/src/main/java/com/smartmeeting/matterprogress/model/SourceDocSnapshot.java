package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/** 飞书资料读取结果 */
public record SourceDocSnapshot(
        String configName,
        String feishuDocUrl,
        String plainText
) {
}
