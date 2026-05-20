package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议纪要持久化与 API 暴露配置，绑定 {@code meeting.minute.*} 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.minute")
public class MeetingMinuteProperties {

    /** 是否在库内持久化纪要正文 */
    private boolean persistEnabled = true;

    /** GET /minute 是否返回全文（false 时仅返回 docUrl 等元数据） */
    private boolean exposeContentInApi = true;
}
