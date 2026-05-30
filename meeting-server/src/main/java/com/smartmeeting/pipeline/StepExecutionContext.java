package com.smartmeeting.pipeline;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.PipelineStep;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StepExecutionContext {
    String meetingId;
    String stage;
    PipelineStep step;
    /**
     * 当前会议快照，供步骤读取标题/群/时间等上下文。
     */
    Meeting meeting;
    /**
     * 同一 meeting+stage 的已完成步骤上下文合并 JSON（字符串）。
     */
    String sharedContextJson;
}
