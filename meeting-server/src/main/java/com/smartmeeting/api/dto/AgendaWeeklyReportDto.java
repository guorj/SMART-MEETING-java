package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 会序绑定的会前事项对比通报（bot 写回的 {@code generated_report_url}）内嵌展示数据。
 */
@Data
@Builder
public class AgendaWeeklyReportDto {
  /** bot 写回的飞书 Doc URL */
  private String generatedReportUrl;
  /** ISO-8601 或 yyyy-MM-dd HH:mm:ss */
  private String generatedReportAt;
  /** 拉取到的 docx/wiki 纯文本（Markdown 或纯文本） */
  private String plainText;
  /** 拉取失败时的说明（仍可有 generatedReportUrl 外链） */
  private String fetchError;
}
