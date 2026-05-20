package com.smartmeeting.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 会议 Web 页面 URL 构建工具。
 * <p>
 * 生成录音页、主持页、个人入会页的完整 URL（含可选版本参数，便于发新链破浏览器缓存）。
 */
@Component
public class MeetingWebPageUrls {

    @Value("${meeting.base-url:http://localhost:8765}")
    private String meetingBaseUrl;

    /** 非空时拼在查询串末尾 {@code &v=}，改值后需重新取录音链接或走飞书重新开会以写库 */
    @Value("${meeting.web.page-cache-buster:}")
    private String pageCacheBuster;

    /**
     * 获取规范化后的会议服务基地址（去除末尾斜杠，空值回退 localhost:8765）。
     *
     * @return 不含末尾 {@code /} 的基地址
     */
    public String normalizedBase() {
        String base = meetingBaseUrl == null ? "" : meetingBaseUrl.trim();
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? "http://localhost:8765" : base;
    }

    /**
     * 构建录音页完整 URL。
     *
     * @param meetingId 会议 ID
     * @param token     录音页 JWT token
     * @return 含 token 及可选缓存破除参数的录音页 URL
     */
    public String recordingPageUrl(String meetingId, String token) {
        return appendBuster(normalizedBase() + "/rec/" + meetingId + "?token=" + token);
    }

    /**
     * 构建 AI 主持页完整 URL。
     *
     * @param meetingId 会议 ID
     * @param token     主持页 JWT token
     * @return 含 token 及可选缓存破除参数的主持页 URL
     */
    public String hostPageUrl(String meetingId, String token) {
        return appendBuster(normalizedBase() + "/host/" + meetingId + "?token=" + token);
    }

    /**
     * 构建线上参会人个人入会页 URL（到场确认，不推流）。
     *
     * @param meetingId 会议 ID
     * @param token     个人入会 JWT token
     * @return 含 token 及可选缓存破除参数的个人入会页 URL
     */
    public String joinPageUrl(String meetingId, String token) {
        return appendBuster(normalizedBase() + "/join/" + meetingId + "?token=" + token);
    }

    private String appendBuster(String url) {
        if (!StringUtils.hasText(pageCacheBuster)) {
            return url;
        }
        String v = URLEncoder.encode(pageCacheBuster.trim(), StandardCharsets.UTF_8);
        return url + "&v=" + v;
    }
}
