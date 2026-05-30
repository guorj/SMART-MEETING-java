package com.smartmeeting.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.PipelineStepExecution;
import com.smartmeeting.repository.PipelineStepExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 将外部回调（飞书卡片等）路由到 WAITING_CALLBACK 步骤执行记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineCallbackRouter {

    private final PipelineStepExecutionMapper executionMapper;
    private final PipelineStepDispatcher pipelineStepDispatcher;
    private final ObjectMapper objectMapper;

    /**
     * 按 callbackKey 查找等待中的步骤并标记成功，再继续调度后续步骤。
     *
     * @return 成功推进返回 true
     */
    @Transactional
    public boolean completeByCallbackKey(String callbackKey, String operatorId, JsonNode payload) {
        if (callbackKey == null || callbackKey.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<PipelineStepExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineStepExecution::getStatus, "WAITING_CALLBACK")
                .orderByAsc(PipelineStepExecution::getId)
                .last("LIMIT 200");
        List<PipelineStepExecution> waitingRows = executionMapper.selectList(wrapper);
        for (PipelineStepExecution row : waitingRows) {
            String rowCallbackKey = extractCallbackKey(row.getContextJson());
            if (!callbackKey.equals(rowCallbackKey)) {
                continue;
            }
            row.setStatus("SUCCESS");
            row.setEndedAt(LocalDateTime.now());
            row.setLastError(null);
            row.setContextJson(mergeContext(row.getContextJson(), callbackPayloadJson(operatorId, payload)));
            executionMapper.updateById(row);
            pipelineStepDispatcher.dispatchPending(row.getMeetingId(), row.getStage());
            log.info("Pipeline callback completed: executionId={}, callbackKey={}, meetingId={}, stage={}",
                    row.getId(), callbackKey, row.getMeetingId(), row.getStage());
            return true;
        }
        return false;
    }

    private String extractCallbackKey(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return "";
        }
        try {
            JsonNode n = objectMapper.readTree(contextJson);
            return n.path("callbackKey").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String callbackPayloadJson(String operatorId, JsonNode payload) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("callbackOperator", operatorId == null ? "" : operatorId);
        if (payload != null && !payload.isNull()) {
            n.set("callbackPayload", payload);
        }
        return n.toString();
    }

    private String mergeContext(String baseJson, String deltaJson) {
        try {
            ObjectNode base = objectMapper.createObjectNode();
            if (baseJson != null && !baseJson.isBlank()) {
                JsonNode b = objectMapper.readTree(baseJson);
                if (b != null && b.isObject()) {
                    base.setAll((ObjectNode) b);
                }
            }
            if (deltaJson != null && !deltaJson.isBlank()) {
                JsonNode d = objectMapper.readTree(deltaJson);
                if (d != null && d.isObject()) {
                    base.setAll((ObjectNode) d);
                }
            }
            return base.toString();
        } catch (Exception e) {
            return baseJson;
        }
    }
}

