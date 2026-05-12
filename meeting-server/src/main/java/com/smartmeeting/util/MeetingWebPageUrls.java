package com.smartmeeting.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 录音页 / 主持页完整 URL（含可选版本参数，便于发新链破浏览器缓存）。
 */
@Component
public class MeetingWebPageUrls {

    @Value("${meeting.base-url:http://localhost:8765}")
    private String meetingBaseUrl;

    /** 非空时拼在查询串末尾 {@code &v=}，改值后需重新取录音链接或走飞书重新开会以写库 */
    @Value("${meeting.web.page-cache-buster:}")
    private String pageCacheBuster;

    public String normalizedBase() {
        String base = meetingBaseUrl == null ? "" : meetingBaseUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? "http://localhost:8765" : base;
    }

    public String recordingPageUrl(String meetingId, String token) {
        return appendBuster(normalizedBase() + "/rec/" + meetingId + "?token=" + token);
    }

    public String hostPageUrl(String meetingId, String token) {
        return appendBuster(normalizedBase() + "/host/" + meetingId + "?token=" + token);
    }

    private String appendBuster(String url) {
        if (!StringUtils.hasText(pageCacheBuster)) {
            return url;
        }
        String v = URLEncoder.encode(pageCacheBuster.trim(), StandardCharsets.UTF_8);
        return url + "&v=" + v;
    }
}
