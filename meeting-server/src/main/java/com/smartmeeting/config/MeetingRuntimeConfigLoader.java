package com.smartmeeting.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import com.smartmeeting.service.DashboardGrantService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MeetingRuntimeConfigLoader {

    private final MeetingSystemConfigMapper configMapper;
    private final MeetingRuntimeConfig runtimeConfig;
    private final MeetingMinuteProperties minuteProperties;
    private final MeetingAsrProperties asrProperties;
    private final MeetingTodoProperties todoProperties;
    private final MeetingPipelineProperties pipelineProperties;
    private final MeetingSchedulerProperties schedulerProperties;
    private final MeetingNotificationProperties notificationProperties;
    private final MeetingVoiceprintProperties voiceprintProperties;
    private final MeetingIsvProperties isvProperties;
    private final MeetingWebProperties webProperties;
    private final MeetingVcProperties vcProperties;
    private final OpenClawProperties openClawProperties;
    private final ObjectMapper objectMapper;
    private final DashboardGrantService dashboardGrantService;

    public MeetingRuntimeConfigLoader(MeetingSystemConfigMapper configMapper,
                                      MeetingRuntimeConfig runtimeConfig,
                                      MeetingMinuteProperties minuteProperties,
                                      MeetingAsrProperties asrProperties,
                                      MeetingTodoProperties todoProperties,
                                      MeetingPipelineProperties pipelineProperties,
                                      MeetingSchedulerProperties schedulerProperties,
                                      MeetingNotificationProperties notificationProperties,
                                      MeetingVoiceprintProperties voiceprintProperties,
                                      MeetingIsvProperties isvProperties,
                                      MeetingWebProperties webProperties,
                                      MeetingVcProperties vcProperties,
                                      OpenClawProperties openClawProperties,
                                      ObjectMapper objectMapper,
                                      @Lazy DashboardGrantService dashboardGrantService) {
        this.configMapper = configMapper;
        this.runtimeConfig = runtimeConfig;
        this.minuteProperties = minuteProperties;
        this.asrProperties = asrProperties;
        this.todoProperties = todoProperties;
        this.pipelineProperties = pipelineProperties;
        this.schedulerProperties = schedulerProperties;
        this.notificationProperties = notificationProperties;
        this.voiceprintProperties = voiceprintProperties;
        this.isvProperties = isvProperties;
        this.webProperties = webProperties;
        this.vcProperties = vcProperties;
        this.openClawProperties = openClawProperties;
        this.objectMapper = objectMapper;
        this.dashboardGrantService = dashboardGrantService;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        resetToFactoryDefaults();
        dashboardGrantService.reload();

        List<MeetingSystemConfig> rows;
        try {
            rows = configMapper.selectList(null);
        } catch (Exception e) {
            log.warn("runtime config reload skipped: {}", e.getMessage());
            return;
        }
        if (rows == null || rows.isEmpty()) {
            log.debug("runtime config reload: no DB overrides, using factory defaults");
            enforceEffectiveConfigCascade();
            return;
        }
        int applied = 0;
        for (MeetingSystemConfig row : rows) {
            if (DashboardGrantService.CONFIG_KEY.equals(row.getConfigKey())) {
                continue;
            }
            if (apply(row.getConfigKey(), row.getValueJson())) {
                applied++;
            }
        }
        enforceEffectiveConfigCascade();
        log.info("runtime config reload applied {} mapped row(s) of {}", applied, rows.size());
    }

    /**
     * DB 各行独立写入后，按 Admin 级联语义修正内存态：总开关关闭时子开关视为关闭。
     */
    private void enforceEffectiveConfigCascade() {
        if (!runtimeConfig.isEnabled()) {
            runtimeConfig.setAgendaEnabled(false);
            runtimeConfig.setTtsEnabled(false);
            runtimeConfig.setRollCallEnabled(false);
            runtimeConfig.setAutoRollCallAfterOpening(false);
        } else if (!runtimeConfig.isRollCallEnabled()) {
            runtimeConfig.setAutoRollCallAfterOpening(false);
        }
    }

    /**
     * 将热更字段重置为 Java {@code @ConfigurationProperties} 出厂默认；
     * infra 项（如 scheduler.scan-ms、notification.bot-base-url）不在此重置。
     */
    private void resetToFactoryDefaults() {
        copyHost(new MeetingRuntimeConfig(), runtimeConfig);
        copyBean(new MeetingMinuteProperties(), minuteProperties);
        copyBean(new MeetingAsrProperties(), asrProperties);
        copyBean(new MeetingTodoProperties(), todoProperties);

        MeetingPipelineProperties pipelineFactory = new MeetingPipelineProperties();
        pipelineProperties.setPreOnCreateEnabled(pipelineFactory.isPreOnCreateEnabled());
        pipelineProperties.getPostAutoTrigger().setEnabled(
                pipelineFactory.getPostAutoTrigger().isEnabled());
        pipelineProperties.getPostAutoTrigger().setTemplateCode(
                pipelineFactory.getPostAutoTrigger().getTemplateCode());

        MeetingSchedulerProperties schedulerFactory = new MeetingSchedulerProperties();
        schedulerProperties.setPreEnabled(schedulerFactory.isPreEnabled());
        schedulerProperties.setPreWindowMinutes(schedulerFactory.getPreWindowMinutes());
        schedulerProperties.setPre24hTemplateCode(schedulerFactory.getPre24hTemplateCode());
        schedulerProperties.setPre10mTemplateCode(schedulerFactory.getPre10mTemplateCode());

        MeetingNotificationProperties notificationFactory = new MeetingNotificationProperties();
        notificationProperties.setBotEnabled(notificationFactory.isBotEnabled());
        notificationProperties.setFallbackDirect(notificationFactory.isFallbackDirect());

        voiceprintProperties.setOfflineLabelEnabled(new MeetingVoiceprintProperties().isOfflineLabelEnabled());
        copyBean(new MeetingVoiceprintProperties(), voiceprintProperties);

        copyBean(new MeetingIsvProperties(), isvProperties);

        copyBean(new MeetingWebProperties(), webProperties);
        copyBean(new MeetingVcProperties(), vcProperties);
        copyBean(new OpenClawProperties(), openClawProperties);
    }

    private boolean apply(String key, String valueJson) {
        if (key == null || valueJson == null || key.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(valueJson);
            switch (key) {
                case "meeting.host.enabled" -> runtimeConfig.setEnabled(parseBoolean(node));
                case "meeting.host.agenda-enabled" -> runtimeConfig.setAgendaEnabled(parseBoolean(node));
                case "meeting.host.tts-enabled" -> runtimeConfig.setTtsEnabled(parseBoolean(node));
                case "meeting.host.roll-call-enabled" -> runtimeConfig.setRollCallEnabled(parseBoolean(node));
                case "meeting.host.auto-roll-call-after-opening" ->
                        runtimeConfig.setAutoRollCallAfterOpening(parseBoolean(node));
                case "meeting.host.topic-timeout-strategy" ->
                        runtimeConfig.setTopicTimeoutStrategy(parseString(node, "REMIND_ONLY"));
                case "meeting.host.roll-call.window-seconds" ->
                        runtimeConfig.getRollCall().setWindowSeconds(parseInt(node));
                case "meeting.host.roll-call.asr-grace-seconds" ->
                        runtimeConfig.getRollCall().setAsrGraceSeconds(parseInt(node));
                case "meeting.host.roll-call.online-inventory-seconds" ->
                        runtimeConfig.getRollCall().setOnlineInventorySeconds(parseInt(node));
                case "meeting.host.reminder.topic-minutes-left" ->
                        runtimeConfig.getReminder().setTopicMinutesLeft(parseInt(node));
                case "meeting.host.reminder.meeting-minutes-left" ->
                        runtimeConfig.getReminder().setMeetingMinutesLeft(parseString(node, ""));
                case "meeting.minute.generation-enabled" -> minuteProperties.setGenerationEnabled(parseBoolean(node));
                case "meeting.minute.llm-enabled" -> minuteProperties.setLlmEnabled(parseBoolean(node));
                case "meeting.minute.ai-enhancement-enabled" ->
                        minuteProperties.setAiEnhancementEnabled(parseBoolean(node));
                case "meeting.minute.feishu-doc-enabled" -> minuteProperties.setFeishuDocEnabled(parseBoolean(node));
                case "meeting.minute.notify-enabled" -> minuteProperties.setNotifyEnabled(parseBoolean(node));
                case "meeting.minute.persist-enabled" -> minuteProperties.setPersistEnabled(parseBoolean(node));
                case "meeting.minute.expose-content-in-api" ->
                        minuteProperties.setExposeContentInApi(parseBoolean(node));
                case "meeting.asr.realtime-enabled" -> asrProperties.setRealtimeEnabled(parseBoolean(node));
                case "meeting.asr.offline-enabled" -> asrProperties.setOfflineEnabled(parseBoolean(node));
                case "meeting.asr.offline-role-enabled" -> asrProperties.setOfflineRoleEnabled(parseBoolean(node));
                case "meeting.asr.offline-role-mode" -> asrProperties.setOfflineRoleMode(parseString(node, "auto"));
                case "meeting.asr.offline-role-num-hint-enabled" ->
                        asrProperties.setOfflineRoleNumHintEnabled(parseBoolean(node));
                case "meeting.asr.offline-ist-max-role-num" ->
                        asrProperties.setOfflineIstMaxRoleNum(parseInt(node));
                case "meeting.asr.offline-ist-max-feature-ids" ->
                        asrProperties.setOfflineIstMaxFeatureIds(parseInt(node));
                case "meeting.asr.offline-poll-max-retries" ->
                        asrProperties.setOfflinePollMaxRetries(parseInt(node));
                case "meeting.asr.offline-poll-interval-ms" ->
                        asrProperties.setOfflinePollIntervalMs(parseInt(node));
                case "meeting.todo.extraction-enabled" -> todoProperties.setExtractionEnabled(parseBoolean(node));
                case "meeting.pipeline.pre-on-create-enabled" ->
                        pipelineProperties.setPreOnCreateEnabled(parseBoolean(node));
                case "meeting.pipeline.post-auto-trigger.enabled" ->
                        pipelineProperties.getPostAutoTrigger().setEnabled(parseBoolean(node));
                case "meeting.pipeline.post-auto-trigger.template-code" ->
                        pipelineProperties.getPostAutoTrigger().setTemplateCode(parseString(node, ""));
                case "meeting.scheduler.pre-enabled" -> schedulerProperties.setPreEnabled(parseBoolean(node));
                case "meeting.scheduler.pre-window-minutes" -> schedulerProperties.setPreWindowMinutes(parseInt(node));
                case "meeting.scheduler.pre-24h-template-code" ->
                        schedulerProperties.setPre24hTemplateCode(parseString(node, ""));
                case "meeting.scheduler.pre-10m-template-code" ->
                        schedulerProperties.setPre10mTemplateCode(parseString(node, ""));
                case "meeting.notification.bot-enabled" -> notificationProperties.setBotEnabled(parseBoolean(node));
                case "meeting.notification.fallback-direct" ->
                        notificationProperties.setFallbackDirect(parseBoolean(node));
                case "meeting.voiceprint.offline-label-enabled" ->
                        voiceprintProperties.setOfflineLabelEnabled(parseBoolean(node));
                case "meeting.voiceprint.offline-min-slice-ms" ->
                        voiceprintProperties.setOfflineMinSliceMs(parseInt(node));
                case "meeting.voiceprint.offline-max-slice-ms" ->
                        voiceprintProperties.setOfflineMaxSliceMs(parseInt(node));
                case "meeting.voiceprint.offline-max-speakers" ->
                        voiceprintProperties.setOfflineMaxSpeakers(parseInt(node));
                case "meeting.voiceprint.offline-vote-slices" ->
                        voiceprintProperties.setOfflineVoteSlices(parseInt(node));
                case "meeting.voiceprint.offline-segment-relabel-enabled" ->
                        voiceprintProperties.setOfflineSegmentRelabelEnabled(parseBoolean(node));
                case "meeting.voiceprint.offline-split-cluster-enabled" ->
                        voiceprintProperties.setOfflineSplitClusterEnabled(parseBoolean(node));
                case "meeting.voiceprint.offline-split-min-segments" ->
                        voiceprintProperties.setOfflineSplitMinSegments(parseInt(node));
                case "meeting.voiceprint.offline-min-slice-floor-ms" ->
                        voiceprintProperties.setOfflineMinSliceFloorMs(parseInt(node));
                case "meeting.isv.enabled" -> isvProperties.setEnabled(parseBoolean(node));
                case "meeting.isv.match-score-threshold" -> isvProperties.setMatchScoreThreshold(parseDouble(node));
                case "meeting.isv.search-top-k-max" -> isvProperties.setSearchTopKMax(parseInt(node));
                case "meeting.isv.search-top-k-min" -> isvProperties.setSearchTopKMin(parseInt(node));
                case "meeting.isv.min-slice-bytes" -> isvProperties.setMinSliceBytes(parseInt(node));
                case "meeting.isv.min-segment-ms-for-slice" -> isvProperties.setMinSegmentMsForSlice(parseInt(node));
                case "meeting.web.static-cache-seconds" -> webProperties.setStaticCacheSeconds(parseInt(node));
                case "meeting.web.page-cache-buster" -> webProperties.setPageCacheBuster(parseString(node, ""));
                case "meeting.vc.recording-enabled" -> vcProperties.setRecordingEnabled(parseBoolean(node));
                case "meeting.vc.auto-record" -> vcProperties.setAutoRecord(parseBoolean(node));
                case "meeting.vc.callback-timeout-min" -> vcProperties.setCallbackTimeoutMin(parseInt(node));
                case "openclaw.enabled" -> openClawProperties.setEnabled(parseBoolean(node));
                case "openclaw.skill-mode" -> openClawProperties.setSkillMode(parseBoolean(node));
                case "openclaw.timeout-seconds" -> openClawProperties.setTimeoutSeconds(parseInt(node));
                case "openclaw.max-concurrent-invokes" -> openClawProperties.setMaxConcurrentInvokes(parseInt(node));
                default -> {
                    log.trace("runtime config key not mapped: {}", key);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("skip invalid runtime config {}={}: {}", key, valueJson, e.getMessage());
            return false;
        }
    }

    private static boolean parseBoolean(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return Boolean.parseBoolean(node.asText());
    }

    private static int parseInt(JsonNode node) {
        if (node.isNumber()) {
            return node.intValue();
        }
        return Integer.parseInt(node.asText().trim());
    }

    private static double parseDouble(JsonNode node) {
        if (node.isNumber()) {
            return node.doubleValue();
        }
        return Double.parseDouble(node.asText().trim());
    }

    private static String parseString(JsonNode node, String defaultValue) {
        if (node.isNull()) {
            return defaultValue;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.asText(defaultValue);
    }

    private static String stripJsonString(JsonNode node) {
        return parseString(node, "");
    }

    private static void copyBean(Object from, Object to) {
        if (from != null && to != null) {
            BeanUtils.copyProperties(from, to);
        }
    }

    private static void copyHost(MeetingRuntimeConfig from, MeetingRuntimeConfig to) {
        to.setEnabled(from.isEnabled());
        to.setAgendaEnabled(from.isAgendaEnabled());
        to.setTtsEnabled(from.isTtsEnabled());
        to.setRollCallEnabled(from.isRollCallEnabled());
        to.setAutoRollCallAfterOpening(from.isAutoRollCallAfterOpening());
        to.setTopicTimeoutStrategy(from.getTopicTimeoutStrategy());
        to.getRollCall().setWindowSeconds(from.getRollCall().getWindowSeconds());
        to.getRollCall().setAsrGraceSeconds(from.getRollCall().getAsrGraceSeconds());
        to.getRollCall().setOnlineInventorySeconds(from.getRollCall().getOnlineInventorySeconds());
        to.getReminder().setTopicMinutesLeft(from.getReminder().getTopicMinutesLeft());
        to.getReminder().setMeetingMinutesLeft(from.getReminder().getMeetingMinutesLeft());
    }
}
