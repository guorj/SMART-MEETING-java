package com.smartmeeting.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java {@code @ConfigurationProperties} 出厂默认须与 Admin Descriptor {@code defaultValue()} 一致。
 * prod seed（{@code meeting-runtime-defaults-prod.sql}）可刻意偏离，不在此断言。
 */
class RuntimeConfigFactoryDefaultsTest {

    @Test
    void hostFactoryDefaultsMatchDescriptors() {
        MeetingRuntimeConfig host = new MeetingRuntimeConfig();
        assertTrue(host.isEnabled());
        assertTrue(host.isAgendaEnabled());
        assertTrue(host.isTtsEnabled());
        assertTrue(host.isRollCallEnabled());
        assertTrue(host.isAutoRollCallAfterOpening());
        assertEquals("REMIND_ONLY", host.getTopicTimeoutStrategy());
        assertEquals(12, host.getRollCall().getWindowSeconds());
        assertEquals(6, host.getRollCall().getAsrGraceSeconds());
        assertEquals(60, host.getRollCall().getOnlineInventorySeconds());
        assertEquals(1, host.getReminder().getTopicMinutesLeft());
        assertEquals("10,3", host.getReminder().getMeetingMinutesLeft());
    }

    @Test
    void minuteFactoryDefaultsMatchDescriptors() {
        MeetingMinuteProperties m = new MeetingMinuteProperties();
        assertFalse(m.isGenerationEnabled());
        assertTrue(m.isPersistEnabled());
        assertTrue(m.isExposeContentInApi());
        assertTrue(m.isLlmEnabled());
        assertTrue(m.isAiEnhancementEnabled());
        assertTrue(m.isFeishuDocEnabled());
        assertTrue(m.isNotifyEnabled());
    }

    @Test
    void asrTodoPipelineSchedulerNotificationDefaultsMatchDescriptors() {
        MeetingAsrProperties asr = new MeetingAsrProperties();
        assertFalse(asr.isRealtimeEnabled());
        assertTrue(asr.isOfflineEnabled());
        assertTrue(asr.isOfflineRoleEnabled());
        assertEquals("auto", asr.getOfflineRoleMode());
        assertTrue(asr.isOfflineRoleNumHintEnabled());

        MeetingTodoProperties todo = new MeetingTodoProperties();
        assertFalse(todo.isExtractionEnabled());

        MeetingPipelineProperties pipeline = new MeetingPipelineProperties();
        assertFalse(pipeline.isPreOnCreateEnabled());
        assertFalse(pipeline.getPostAutoTrigger().isEnabled());
        assertEquals("", pipeline.getPostAutoTrigger().getTemplateCode());

        MeetingSchedulerProperties scheduler = new MeetingSchedulerProperties();
        assertFalse(scheduler.isPreEnabled());
        assertEquals(5, scheduler.getPreWindowMinutes());
        assertEquals("", scheduler.getPre24hTemplateCode());
        assertEquals("", scheduler.getPre10mTemplateCode());

        MeetingNotificationProperties notification = new MeetingNotificationProperties();
        assertFalse(notification.isBotEnabled());
        assertTrue(notification.isFallbackDirect());
    }

    @Test
    void voiceprintIsvWebOpenClawDefaultsMatchDescriptors() {
        MeetingVoiceprintProperties voiceprint = new MeetingVoiceprintProperties();
        assertTrue(voiceprint.isOfflineLabelEnabled());
        assertEquals(3000, voiceprint.getOfflineMinSliceMs());
        assertEquals(8, voiceprint.getOfflineMaxSpeakers());

        MeetingIsvProperties isv = new MeetingIsvProperties();
        assertFalse(isv.isEnabled());
        assertEquals(0.6, isv.getMatchScoreThreshold(), 0.001);
        assertEquals(10, isv.getSearchTopKMax());
        assertEquals(3, isv.getSearchTopKMin());

        MeetingAsrProperties asr = new MeetingAsrProperties();
        assertEquals(10, asr.getOfflineIstMaxRoleNum());
        assertEquals(64, asr.getOfflineIstMaxFeatureIds());
        assertEquals(60, asr.getOfflinePollMaxRetries());
        assertEquals(5000, asr.getOfflinePollIntervalMs());
        assertEquals(60000, asr.getRealtime().getPostOpenWaitMaxMs());

        MeetingWebProperties web = new MeetingWebProperties();
        assertEquals(300, web.getStaticCacheSeconds());
        assertEquals("", web.getPageCacheBuster());

        OpenClawProperties openClaw = new OpenClawProperties();
        assertTrue(openClaw.isEnabled());
        assertTrue(openClaw.isSkillMode());
        assertEquals(60, openClaw.getTimeoutSeconds());
        assertEquals(5, openClaw.getMaxConcurrentInvokes());
        assertEquals(2000, openClaw.getPromptMaxChars());
        assertEquals(24, openClaw.getGateway().getHistoryFetchAttempts());
    }

    @Test
    void infraYamlPropertiesFactoryDefaults() {
        MeetingAudioProperties audio = new MeetingAudioProperties();
        assertEquals(16000, audio.getSampleRate());
        assertEquals(4, audio.getMaxDurationHours());
        assertEquals(72, audio.getCacheRetentionHours());

        MeetingHostRuntimeProperties hostRuntime = new MeetingHostRuntimeProperties();
        assertEquals(6000, hostRuntime.getTtsChunkBytes());

        MatterProgressFetchProperties fetch = new MatterProgressFetchProperties();
        assertEquals(20, fetch.getMaxSheets());
        assertEquals(500, fetch.getAbsMaxRows());
    }
}
