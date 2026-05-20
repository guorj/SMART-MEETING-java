package com.smartmeeting.asr;

import lombok.Getter;
import lombok.Setter;

/**
 * 讯飞实时 ASR 单条转写结果的数据载体。
 * <p>
 * 由 {@link XfyunRealtimeClient} 在解析 WebSocket 响应后填充，经
 * {@link XfyunRealtimeClient#setTranscriptCallback(java.util.function.Consumer)} 注册的回调分发给上层
 *（如会议转写服务、WebSocket 推送等）。
 * </p>
 *
 * @see XfyunRealtimeClient
 */
@Getter
@Setter
public class AsrResult {

    /** 当前会议标识，与连接时传入的 meetingId 一致 */
    private String meetingId;

    /** 本帧识别出的文本内容（可能为中间结果或最终结果） */
    private String text;

    /** 置信度：最终结果通常为 1.0，中间结果通常为 0.5 */
    private Double confidence;

    /** 是否为最终结果（讯飞 st.type=0 表示 final，1 表示 interim） */
    private Boolean finalResult;

    /** 是否为会话最后一条结果（讯飞 data.ls=true） */
    private Boolean isLast;

    /** 片段起始时间（毫秒，对应讯飞 st.bg） */
    private Integer startTimeMs;

    /** 片段结束时间（毫秒，对应讯飞 st.ed） */
    private Integer endTimeMs;

    /** 说话人标识：声纹角色 rl 或 "unknown" */
    private String speakerId;
}
