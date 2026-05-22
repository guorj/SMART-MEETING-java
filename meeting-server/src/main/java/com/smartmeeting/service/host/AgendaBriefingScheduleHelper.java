package com.smartmeeting.service.host;

/**
 * 会序 OpenClaw 通报调度辅助逻辑（可单测）。
 *
 * <p>主持运行时 {@link MeetingHostSessionService} 在预取、切题、写回等路径共用本类，
 * 用于统一缓存命中、预取失败冷却、以及「旧任务覆盖新结果」的防护规则。
 */
final class AgendaBriefingScheduleHelper {

    private AgendaBriefingScheduleHelper() {
    }

    /**
     * 是否可视为缓存命中：状态 ready、正文非空，且配置表 url 与生成时记录的 url 一致。
     *
     * @param briefingStatus   议题上的 {@code briefingStatus}
     * @param briefingMarkdown 已生成的 Markdown
     * @param cachedFeishuUrl  生成成功时写入的 {@code briefingFeishuUrl}
     * @param configFeishuUrl  当前配置表 {@code feishu_doc_url}
     */
    static boolean isCacheHit(String briefingStatus,
                              String briefingMarkdown,
                              String cachedFeishuUrl,
                              String configFeishuUrl) {
        if (!"ready".equals(briefingStatus)) {
            return false;
        }
        if (briefingMarkdown == null || briefingMarkdown.isBlank()) {
            return false;
        }
        return normalizeUrl(configFeishuUrl).equals(normalizeUrl(cachedFeishuUrl));
    }

    /**
     * 预取路径下是否应跳过对「近期失败」会序的立即重试。
     *
     * <p>仅作用于 {@code prefetch=true}：后台预取失败后在冷却期内不再打 Gateway，
     * 避免会议刚开始时对多个后续会序连环重试。用户切到该会序（{@code prefetch=false}）
     * 时仍会立即重试。
     *
     * @param prefetch           是否为预取调度
     * @param briefingStatus     当前状态
     * @param briefingFailedAtMs 最近一次失败时间戳（毫秒），无失败为 0
     * @param nowMs              当前时间
     * @param cooldownMs         冷却时长（毫秒），≤0 表示不启用冷却
     */
    static boolean shouldSkipPrefetchRetry(boolean prefetch,
                                          String briefingStatus,
                                          long briefingFailedAtMs,
                                          long nowMs,
                                          long cooldownMs) {
        if (!prefetch || cooldownMs <= 0) {
            return false;
        }
        if (!"failed".equals(briefingStatus)) {
            return false;
        }
        if (briefingFailedAtMs <= 0) {
            return false;
        }
        return nowMs - briefingFailedAtMs < cooldownMs;
    }

    /**
     * 异步任务写回前校验：代次一致且配置 url 未变，才允许覆盖议题上的通报字段。
     *
     * <p>防止「task-1（旧 url）晚于 task-2（新 url）完成」时把新结果盖回旧内容。
     *
     * @param taskGeneration    本任务启动时捕获的 {@code briefingGeneration}
     * @param currentGeneration 写回时刻议题上的代次
     * @param taskFeishuUrl     本任务使用的配置 url
     * @param currentConfigUrl  写回时刻配置表 url
     */
    static boolean shouldApplyTaskResult(int taskGeneration,
                                       int currentGeneration,
                                       String taskFeishuUrl,
                                       String currentConfigUrl) {
        if (taskGeneration <= 0 || taskGeneration != currentGeneration) {
            return false;
        }
        return normalizeUrl(taskFeishuUrl).equals(normalizeUrl(currentConfigUrl));
    }

    /**
     * 写回被拒绝后，本任务是否仍「拥有」该会序且处于 loading，需要置为 failed 以免 UI 卡死。
     *
     * <p>若 {@code currentGeneration > taskGeneration}，说明已有新任务接手，本任务不得改状态。
     */
    static boolean shouldFailLoadingAfterStaleDiscard(int taskGeneration,
                                                      int currentGeneration,
                                                      String briefingStatus) {
        return taskGeneration > 0
                && taskGeneration == currentGeneration
                && "loading".equals(briefingStatus);
    }

    /**
     * 写回因代次/url 校验未通过而丢弃时，展示给主持端的失败原因。
     */
    /** OpenClaw 返回空内容时，预取路径不宜直接标 failed，留待会序 RUNNING 时再调度。 */
    static boolean isOpenClawEmptyError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return false;
        }
        return errorMessage.contains("返回为空") || errorMessage.contains("返回空");
    }

    static String staleDiscardErrorMessage(String taskFeishuUrl, String currentConfigUrl) {
        if (normalizeUrl(currentConfigUrl).isEmpty()) {
            return "OpenClaw 配置已移除，请检查后重试";
        }
        if (!normalizeUrl(taskFeishuUrl).equals(normalizeUrl(currentConfigUrl))) {
            return "飞书资料链接已变更，请重试";
        }
        return "会序通报结果已过期，请重试";
    }

    /** 去掉首尾空白，避免仅因空格差异导致缓存失效或脏写校验失败。 */
    static String normalizeUrl(String url) {
        return url != null ? url.trim() : "";
    }
}
