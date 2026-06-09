package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议 ASR 相关配置，绑定 {@code meeting.asr.*} 中与业务开关相关的项。
 * <p>
 * 讯飞连接参数仍由 {@code meeting.asr.xfyun.*} 等 {@link org.springframework.beans.factory.annotation.Value} 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.asr")
public class MeetingAsrProperties {

    /**
     * 是否启用会中实时转写（浏览器 PCM → 讯飞实时 ASR → 字幕/库表）。
     * 关闭后仍缓存录音文件，会后纪要可走离线 ASR。
     */
    private boolean realtimeEnabled = false;

    /**
     * 是否启用会后离线 ASR（讯飞 IST 上传 PCM → 转写分段入库）。
     * 会议结束时独立触发，不依赖 meeting.minute.generation-enabled；有实时定稿分段时跳过。
     */
    private boolean offlineEnabled = true;

    /** 离线 IST 是否开启说话人分离（false 时 roleType=0）。 */
    private boolean offlineRoleEnabled = true;

    /**
     * 离线 IST 角色分离模式：auto（≥2 声纹用 roleType=3，否则 1）、blind（1）、voiceprint（3，不足则回退 1）。
     */
    private String offlineRoleMode = "auto";

    /** 是否向 IST 传 roleNum（参会人数 hint，上限见 offline-ist-max-role-num）。 */
    private boolean offlineRoleNumHintEnabled = true;

    /** IST upload roleNum 上限（讯飞文档 0–10）。 */
    private int offlineIstMaxRoleNum = 10;

    /** IST upload featureIds 个数上限。 */
    private int offlineIstMaxFeatureIds = 64;

    /** 离线转写轮询最大次数。 */
    private int offlinePollMaxRetries = 60;

    /** 离线转写轮询间隔（毫秒）。 */
    private int offlinePollIntervalMs = 5000;

    private Realtime realtime = new Realtime();

    @Data
    public static class Realtime {
        /** 实时 ASR 握手后 post-open-wait 上限（毫秒）。 */
        private int postOpenWaitMaxMs = 60000;
    }
}
