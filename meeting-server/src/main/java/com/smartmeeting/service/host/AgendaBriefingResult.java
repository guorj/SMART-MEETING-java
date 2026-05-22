package com.smartmeeting.service.host;

import lombok.Builder;
import lombok.Value;

/**
 * 主持会序 OpenClaw 通报生成结果（进程内缓存，经 host_state 下发主持页）。
 */
@Value
@Builder
public class AgendaBriefingResult {
    String markdown;
    /** 数据来源：openclaw_gateway / feishu_plaintext_llm / error */
    String source;
    String errorMessage;

    public boolean isSuccess() {
        return markdown != null && !markdown.isBlank();
    }
}
