package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 会序绑定的会前事项对比通报。
 * <p>v0.26：优先 {@code runId} + {@code items}（库内结构化）；旧 {@code generatedReportUrl} 保留过渡期 fallback。
 */
@Data
@Builder
public class AgendaWeeklyReportDto {
  /** v0.26：最新批次 run id（int_weekly_matter_comparison_run.id） */
  private Long runId;
  /** 批次标题 */
  private String title;
  /** 批次事项总数 */
  private Integer itemCount;
  /** READY|FAILED|PARTIAL */
  private String generationStatus;
  /** 结构化事项列表（按 category, sort_order 排序） */
  private List<WeeklyComparisonItemDto> items;
  /** 旧：bot 写回的飞书 Doc URL（过渡期 fallback） */
  private String generatedReportUrl;
  /** ISO-8601 或 yyyy-MM-dd HH:mm:ss */
  private String generatedReportAt;
  /** 拉取到的 docx/wiki 纯文本（fallback 路径） */
  private String plainText;
  /** 拉取失败时的说明 */
  private String fetchError;
  /** 结构化内容类型（fallback 路径，如 docx_blocks） */
  private String contentType;
  /** 结构化 JSON（fallback 路径） */
  private Object structuredContent;
}
