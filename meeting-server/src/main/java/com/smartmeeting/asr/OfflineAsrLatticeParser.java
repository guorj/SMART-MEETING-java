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
 * {@code st.bg}/{@code st.ed} 为句级起止时间，单位 <b>毫秒</b>（讯飞 IST 文档）。
 * 句级角色取自 {@code st.rl}（roleType=1 说话人分离）。
 */
@Slf4j
public final class OfflineAsrLatticeParser {

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
                String text = extractStText(st);
                if (text.isEmpty()) {
                    continue;
                }
                int startMs = st.path("bg").asInt(0);
                int endMs = st.path("ed").asInt(startMs);
                TranscriptSegment ts = new TranscriptSegment();
                ts.setId(UUID.randomUUID().toString());
                ts.setStartTimeMs(startMs);
                ts.setEndTimeMs(Math.max(startMs, endMs));
                ts.setText(text);
                ts.setSpeakerId(speakerId);
                ts.setConfidence(1.0);
                ts.setIsFinal(true);
                ts.setCorrected(false);
                segments.add(ts);
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

    private static String extractStText(JsonNode st) {
        StringBuilder text = new StringBuilder();
        JsonNode rtList = st.path("rt");
        if (!rtList.isArray()) {
            return "";
        }
        for (JsonNode rt : rtList) {
            JsonNode wsList = rt.path("ws");
            if (!wsList.isArray()) {
                continue;
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
