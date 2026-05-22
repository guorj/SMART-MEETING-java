package com.smartmeeting.service.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgendaBriefingScheduleHelper} 单元测试：缓存、冷却、写回代次校验。
 */
class AgendaBriefingScheduleHelperTest {

    @Test
    @DisplayName("ready + 正文 + url 一致 → 缓存命中")
    void isCacheHit_whenReadyAndUrlMatch() {
        assertThat(AgendaBriefingScheduleHelper.isCacheHit(
                "ready", "# 通报", " https://x.feishu.cn/base/a ", "https://x.feishu.cn/base/a"))
                .isTrue();
    }

    @Test
    @DisplayName("url 不一致 → 不命中")
    void isCacheHit_whenUrlMismatch() {
        assertThat(AgendaBriefingScheduleHelper.isCacheHit(
                "ready", "# 通报", "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/b"))
                .isFalse();
    }

    @Test
    @DisplayName("failed 或空正文 → 不命中")
    void isCacheHit_whenNotReady() {
        assertThat(AgendaBriefingScheduleHelper.isCacheHit(
                "failed", "# 通报", "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/a"))
                .isFalse();
        assertThat(AgendaBriefingScheduleHelper.isCacheHit(
                "ready", "  ", "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/a"))
                .isFalse();
    }

    @Test
    @DisplayName("预取 + 近期 failed → 冷却期内跳过")
    void shouldSkipPrefetchRetry_withinCooldown() {
        long now = 100_000L;
        long failedAt = 70_000L;
        assertThat(AgendaBriefingScheduleHelper.shouldSkipPrefetchRetry(
                true, "failed", failedAt, now, 60_000L)).isTrue();
    }

    @Test
    @DisplayName("非预取或冷却已过 → 不跳过")
    void shouldSkipPrefetchRetry_retryAllowed() {
        long now = 100_000L;
        long failedAt = now - 90_000L;
        assertThat(AgendaBriefingScheduleHelper.shouldSkipPrefetchRetry(
                false, "failed", failedAt, now, 60_000L)).isFalse();
        assertThat(AgendaBriefingScheduleHelper.shouldSkipPrefetchRetry(
                true, "failed", failedAt, now, 60_000L)).isFalse();
        assertThat(AgendaBriefingScheduleHelper.shouldSkipPrefetchRetry(
                true, "loading", failedAt, now, 60_000L)).isFalse();
    }

    @Test
    @DisplayName("代次一致且 url 未变 → 允许写回")
    void shouldApplyTaskResult_whenGenerationAndUrlMatch() {
        assertThat(AgendaBriefingScheduleHelper.shouldApplyTaskResult(
                2, 2, "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/a"))
                .isTrue();
    }

    @Test
    @DisplayName("代次不一致或 url 已变 → 丢弃写回")
    void shouldApplyTaskResult_whenStale() {
        assertThat(AgendaBriefingScheduleHelper.shouldApplyTaskResult(
                1, 2, "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/a"))
                .isFalse();
        assertThat(AgendaBriefingScheduleHelper.shouldApplyTaskResult(
                2, 2, "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/b"))
                .isFalse();
        assertThat(AgendaBriefingScheduleHelper.shouldApplyTaskResult(
                0, 1, "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/a"))
                .isFalse();
    }

    @Test
    @DisplayName("丢弃写回：本任务代次且 loading → 应置 failed")
    void shouldFailLoadingAfterStaleDiscard_whenOwningLoading() {
        assertThat(AgendaBriefingScheduleHelper.shouldFailLoadingAfterStaleDiscard(
                2, 2, "loading")).isTrue();
        assertThat(AgendaBriefingScheduleHelper.shouldFailLoadingAfterStaleDiscard(
                1, 2, "loading")).isFalse();
        assertThat(AgendaBriefingScheduleHelper.shouldFailLoadingAfterStaleDiscard(
                2, 2, "ready")).isFalse();
    }

    @Test
    @DisplayName("OpenClaw 返回为空错误识别")
    void isOpenClawEmptyError() {
        assertThat(AgendaBriefingScheduleHelper.isOpenClawEmptyError("OpenClaw 返回为空")).isTrue();
        assertThat(AgendaBriefingScheduleHelper.isOpenClawEmptyError("Gateway 不可用")).isFalse();
    }

    @Test
    @DisplayName("丢弃写回失败文案：配置移除 / url 变更")
    void staleDiscardErrorMessage() {
        assertThat(AgendaBriefingScheduleHelper.staleDiscardErrorMessage(
                "https://x.feishu.cn/base/a", ""))
                .contains("配置已移除");
        assertThat(AgendaBriefingScheduleHelper.staleDiscardErrorMessage(
                "https://x.feishu.cn/base/a", "https://x.feishu.cn/base/b"))
                .contains("链接已变更");
    }
}
