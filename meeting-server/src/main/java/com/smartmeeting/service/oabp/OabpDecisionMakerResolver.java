package com.smartmeeting.service.oabp;

import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.entity.oabp.OabpJqTodosTask;
import com.smartmeeting.repository.UserMappingMapper;
import com.smartmeeting.repository.oabp.OabpJqTodosTaskMapper;
import com.smartmeeting.service.FeishuUserIdResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 决策人解析器：根据 meeting 待办 ID 查询 OABP 决策人信息。
 * <p>
 * 决策人来源：OABP {@code jq_todos_task.decision_maker_user_id}（Long，关联
 * {@code system_users.id}）。该字段当前由 OABP 端维护，meeting 回写时恒置 0；
 * 值为 0 或解析失败时，待办直接走原完成流程（不推决策人裁决）。
 * </p>
 * <p>
 * <b>解析链路</b>：
 * <ol>
 *   <li>按 remark 前缀 {@code [meeting:todoId=xxx]} 查 {@code jq_todos_task}</li>
 *   <li>读 {@code decision_maker_user_id}，{@code <= 0} 返回 empty</li>
 *   <li>调 {@link FeishuUserIdResolver#resolve(String)} 把 OABP 工号解析为飞书 user_id</li>
 *   <li>查 {@link UserMapping} 取 {@code userName} 作为决策人姓名</li>
 * </ol>
 * </p>
 * <p>
 * <b>启用条件</b>：仅在 {@code meeting.datasource.external.oabp.enabled=true} 时注册。
 * 未启用时业务方调用会拿到 empty（待办直接走原完成流程）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpDecisionMakerResolver {

    /** remark 前缀，与 {@code OabpTaskWritebackConsumer} 保持一致 */
    private static final String REMARK_PREFIX_FORMAT = "[meeting:todoId=%s]";

    private final OabpJqTodosTaskMapper taskMapper;
    private final FeishuUserIdResolver feishuUserIdResolver;
    private final UserMappingMapper userMappingMapper;

    /**
     * 解析指定 meeting 待办的决策人信息。
     *
     * @param meetingTodoId meeting 待办 ID
     * @return 决策人信息（含飞书 user_id 与姓名）；无决策人或解析失败返回 empty
     */
    public Optional<DecisionMaker> resolve(String meetingTodoId) {
        if (meetingTodoId == null || meetingTodoId.isBlank()) {
            return Optional.empty();
        }
        String prefix = String.format(REMARK_PREFIX_FORMAT + "%%", meetingTodoId);
        OabpJqTodosTask task = taskMapper.findByRemarkPrefix(prefix).stream().findFirst().orElse(null);
        if (task == null) {
            log.debug("OabpDecisionMakerResolver task not found for meetingTodoId={}", meetingTodoId);
            return Optional.empty();
        }
        Long decisionMakerOaId = task.getDecisionMakerUserId();
        if (decisionMakerOaId == null || decisionMakerOaId <= 0) {
            log.debug("OabpDecisionMakerResolver no decision maker for meetingTodoId={}, oabpTaskId={}",
                    meetingTodoId, task.getId());
            return Optional.empty();
        }
        Optional<String> feishuUserIdOpt = feishuUserIdResolver.resolve(String.valueOf(decisionMakerOaId));
        if (feishuUserIdOpt.isEmpty()) {
            log.warn("OabpDecisionMakerResolver cannot resolve feishuUserId for oaId={}, meetingTodoId={}",
                    decisionMakerOaId, meetingTodoId);
            return Optional.empty();
        }
        String name = resolveName(decisionMakerOaId.intValue());
        return Optional.of(new DecisionMaker(feishuUserIdOpt.get(), name));
    }

    /**
     * 按 OABP 用户 ID 查姓名（从 {@code int_user_mapping_feishu.user_name} 取）。
     *
     * @param oaUserId OABP 系统用户 ID
     * @return 用户姓名；查不到返回 null
     */
    private String resolveName(Integer oaUserId) {
        UserMapping mapping = userMappingMapper.selectById(oaUserId);
        return mapping == null ? null : mapping.getUserName();
    }

    /**
     * 决策人信息载体。
     *
     * @param feishuUserId 决策人飞书 user_id（用于推送裁决卡）
     * @param name         决策人姓名（展示用）
     */
    public record DecisionMaker(String feishuUserId, String name) {
    }
}
