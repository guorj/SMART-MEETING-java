package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书 VC 云端录制接入配置（meeting.vc.*）。
 * <p>
 * 开启后：建会时日历事件设置 {@code vchat.meeting_settings.auto_record=true}，
 * 会后通过 {@code vc.meeting.recording_ready_v1} 回调或 Admin 手动输入 minute_token
 * 拉取妙记音视频文件（File B），覆盖远程参会人声音。
 * <p>
 * 关闭时：建会不传 auto_record，不处理回调，行为与未接入时一致。
 * <p>
 * 详见 {@code docs/feishu-vc-recording-design.md}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.vc")
public class MeetingVcProperties {

    /** 飞书 VC 云端录制总开关。关闭时本配置块所有行为不生效。 */
    private boolean recordingEnabled = false;

    /**
     * 日历事件是否设置 {@code auto_record=true}。
     * 仅 {@code recording-enabled=true} 时生效。
     * 关闭后即使总开关打开也不自动开录（需主持人飞书客户端手动点云录制）。
     */
    private boolean autoRecord = true;

    /**
     * 等待 {@code recording_ready_v1} 回调的超时分钟数。
     * 当前实现不阻塞等待（webhook 迟到时由 Admin 手动补 minute_token），
     * 此值保留供后续阻塞等待实现或文档参考。
     */
    private int callbackTimeoutMin = 15;
}
