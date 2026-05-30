package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingRuntimeConfigLoader;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MidTopicTimeoutStepExecutor implements StepExecutor {

    private final MeetingSystemConfigMapper configMapper;
    private final MeetingRuntimeConfigLoader configLoader;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "mid-topic-timeout";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        var cfg = support.parseConfig(context.getStep().getConfigJson());
        String strategy = support.text(cfg, "strategy", "REMIND_ONLY").toUpperCase();
        int topicWarnMinutes = support.number(cfg, "topicWarnMinutes", 3);
        upsert("meeting.host.topic-timeout-strategy", "\""+ strategy + "\"", "host", "议题超时策略");
        upsert("meeting.host.reminder.topic-minutes-left", String.valueOf(topicWarnMinutes), "host", "议题剩余提醒分钟");
        configLoader.reload();
        return StepExecutionResult.ok("mid-topic-timeout-config-updated",
                "{\"strategy\":\"" + strategy + "\",\"topicWarnMinutes\":" + topicWarnMinutes + "}");
    }

    private void upsert(String key, String valueJson, String category, String desc) {
        MeetingSystemConfig row = configMapper.selectOne(new LambdaQueryWrapper<MeetingSystemConfig>()
                .eq(MeetingSystemConfig::getConfigKey, key)
                .last("LIMIT 1"));
        if (row == null) {
            row = new MeetingSystemConfig();
            row.setConfigKey(key);
            row.setCategory(category);
            row.setDescription(desc);
            row.setValueJson(valueJson);
            configMapper.insert(row);
        } else {
            row.setValueJson(valueJson);
            row.setCategory(category);
            row.setDescription(desc);
            configMapper.updateById(row);
        }
    }
}

