package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

/**
 * 飞书 VC 云端录制（妙记 File B）热更参数。
 * 详见 {@code docs/feishu-vc-recording-design.md}。
 */
@Component
class VcRecordingEnabledDescriptor extends AbstractBooleanDescriptor {
    VcRecordingEnabledDescriptor() {
        super("meeting.vc.recording-enabled", "vc", "false",
                "飞书 VC 云端录制总开关：预约建会设 auto_record、处理 recording_ready、会后拉妙记音视频");
    }
}

@Component
class VcAutoRecordDescriptor extends AbstractBooleanDescriptor {
    VcAutoRecordDescriptor() {
        super("meeting.vc.auto-record", "vc", "true",
                "日历事件 vchat.meeting_settings.auto_record（仅 recording-enabled=true 时生效）",
                "meeting.vc.recording-enabled");
    }
}

@Component
class VcCallbackTimeoutMinDescriptor extends AbstractIntegerDescriptor {
    VcCallbackTimeoutMinDescriptor() {
        super("meeting.vc.callback-timeout-min", "vc", "15",
                "等待 recording_ready_v1 回调超时（分钟）；当前实现不阻塞等待，超时回退浏览器 PCM",
                "meeting.vc.recording-enabled", 1, 120);
    }
}
