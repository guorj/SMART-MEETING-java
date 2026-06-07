package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.TranscriptSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 解析讯飞离线 IST {@code orderResult} 中的 lattice/json_1best，提取文本、时间戳与说话人角色。
 * <p>
 * {@code rt.bg}/{@code rt.ed} 按讯飞办公听写惯例为 <b>10ms 帧</b>，乘 {@link #RT_FRAME_MS} 得到毫秒。
 * 句级角色取自 {@code st.rl}（roleType=1 说话人分离）。
 */
@Slf4j
public final class OfflineAsrLatticeParser {

    /** rt.bg / rt.ed 单帧时长（毫秒）。 */
    public static final int RT_FRAME_MS = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OfflineAsrLatticeParser() {
    }

    /**
     * 解析 orderResult JSON 为转写分段（未设置 meetingId）。
     *
     * @param orderResult getResult 返回的 orderResult 字符串
     * @return 分段列表；解析失败时返回单段降级文本
     */
    public static List<TranscriptSegment> parse(String orderResult) {
        try {
            JsonNode latticeData = MAPPER.readTree(orderResult);
            List<TranscriptSegment> segments = new ArrayList<>();
            JsonNode latticeList = latticeData.path("lattice");
            if (!latticeList.isArray()) {
                return fallbackSingle(orderResult);
            }
            for (JsonNode lattice : latticeList) {
                String json1best = lattice.path("json_1best").asText("");
                if (json1best.isEmpty()) {
                    continue;
                }
                JsonNode best = MAPPER.readTree(json1best);
                JsonNode st = best.path("st");
                String roleLabel = resolveRoleLabel(st);
                String speakerId = "speaker_" + roleLabel;
                JsonNode rtList = st.path("rt");
                if (!rtList.isArray()) {
                    continue;
                }
                for (JsonNode rt : rtList) {
                    String text = extractRtText(rt);
                    if (text.isEmpty()) {
                        continue;
                    }
                    TranscriptSegment ts = new TranscriptSegment();
                    ts.setId(UUID.randomUUID().toString());
                    int bgFrame = rt.path("bg").asInt(0);
                    int edFrame = rt.path("ed").asInt(bgFrame);
                    ts.setStartTimeMs(bgFrame * RT_FRAME_MS);
                    ts.setEndTimeMs(Math.max(ts.getStartTimeMs(), edFrame * RT_FRAME_MS));
                    ts.setText(text);
                    ts.setSpeakerId(speakerId);
                    ts.setConfidence(1.0);
                    ts.setIsFinal(true);
                    ts.setCorrected(false);
                    segments.add(ts);
                }
            }
            log.info("【离线ASR解析】segments={}, distinctSpeakers~={}",
                    segments.size(), segments.stream().map(TranscriptSegment::getSpeakerId).distinct().count());
            if (segments.isEmpty()) {
                return fallbackSingle(orderResult);
            }
            return segments;
        } catch (Exception e) {
            log.error("【离线ASR解析】失败: {}", e.getMessage());
            return fallbackSingle(orderResult);
        }
    }

    private static String resolveRoleLabel(JsonNode st) {
        if (st == null || st.isMissingNode()) {
            return "0";
        }
        String rl = st.path("rl").asText("").trim();
        if (!rl.isEmpty()) {
            return rl;
        }
        String spk = st.path("spk").asText("").trim();
        return spk.isEmpty() ? "0" : spk;
    }

    private static String extractRtText(JsonNode rt) {
        StringBuilder text = new StringBuilder();
        JsonNode wsList = rt.path("ws");
        if (!wsList.isArray()) {
            return "";
        }
        for (JsonNode ws : wsList) {
            JsonNode cwList = ws.path("cw");
            if (!cwList.isArray()) {
                continue;
            }
            for (JsonNode cw : cwList) {
                text.append(cw.path("w").asText(""));
            }
        }
        return text.toString().trim();
    }

    private static List<TranscriptSegment> fallbackSingle(String raw) {
        TranscriptSegment ts = new TranscriptSegment();
        ts.setId(UUID.randomUUID().toString());
        ts.setStartTimeMs(0);
        ts.setEndTimeMs(0);
        ts.setText(raw == null ? "" : raw);
        ts.setSpeakerId("speaker_0");
        ts.setIsFinal(true);
        ts.setCorrected(false);
        ts.setConfidence(1.0);
        return List.of(ts);
    }
}
