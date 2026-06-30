package com.smartmeeting.api.dto.internal;

import lombok.Data;

/**
 * Admin 手动写入妙记 minute_token 请求体。
 */
@Data
public class SetVcMinuteTokenRequest {
    /** 妙记 token（24 字符）；空串视为清除 */
    private String minuteToken;
    /** 妙记页面 URL（可选，便于人工核对） */
    private String recordingUrl;
}
