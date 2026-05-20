package com.smartmeeting.api.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 录音 / 主持 / 入会入口页控制器。
 * <p>
 * 提供 {@code /rec/{meetingId}}、{@code /host/{meetingId}}、{@code /join/{meetingId}} 等 HTML 入口，
 * 不再 forward 到 {@code /static/*.html}：否则仍套用 {@code /static/**} 的缓存周期，浏览器易长期保留旧壳。
 * 此处直接返回 HTML 并 {@code Cache-Control: no-store}，保证入口始终与 JAR 内资源一致。
 */
@Controller
public class RecorderPageController {

    /**
     * 录音页入口（与主持页共用同一 HTML 壳）。
     *
     * @param meetingId 会议 ID（路径占位，前端从 URL 解析）
     * @return classpath 下 {@code static/host-meeting.html}，不存在则 404
     */
    @GetMapping("/rec/{meetingId}")
    public ResponseEntity<Resource> recorderPage(@PathVariable @SuppressWarnings("unused") String meetingId) {
        // 与 /host 同一壳：录音 + 议程主持 + 事项进度通报（飞书「录音链接」与「主持链接」均打开此页）
        return serveClasspathHtml("static/host-meeting.html");
    }

    /**
     * AI 主持页入口（与录音页共用同一 HTML 壳）。
     *
     * @param meetingId 会议 ID（路径占位，前端从 URL 解析）
     * @return classpath 下 {@code static/host-meeting.html}，不存在则 404
     */
    @GetMapping("/host/{meetingId}")
    public ResponseEntity<Resource> hostMeetingPage(@PathVariable @SuppressWarnings("unused") String meetingId) {
        return serveClasspathHtml("static/host-meeting.html");
    }

    /**
     * 线上参会人个人入会页入口。
     *
     * @param meetingId 会议 ID（路径占位，前端从 URL 解析 token）
     * @return classpath 下 {@code static/join-meeting.html}，不存在则 404
     */
    @GetMapping("/join/{meetingId}")
    public ResponseEntity<Resource> joinPage(@PathVariable @SuppressWarnings("unused") String meetingId) {
        return serveClasspathHtml("static/join-meeting.html");
    }

    /**
     * 从 classpath 读取 HTML 并以 no-store 缓存策略返回。
     *
     * @param classpathPath 类路径资源路径，如 {@code static/host-meeting.html}
     * @return 200 带 HTML 正文，或 404
     */
    private static ResponseEntity<Resource> serveClasspathHtml(String classpathPath) {
        Resource body = new ClassPathResource(classpathPath);
        if (!body.exists()) {
            return ResponseEntity.notFound().build();
        }
        CacheControl cc = CacheControl.noStore()
                .mustRevalidate()
                .sMaxAge(0, TimeUnit.SECONDS);
        return ResponseEntity.ok()
                .cacheControl(cc)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(body);
    }
}
