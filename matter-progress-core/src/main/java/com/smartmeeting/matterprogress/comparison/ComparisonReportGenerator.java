package com.smartmeeting.matterprogress.comparison;

import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.SourceDocSnapshot;

import java.util.List;

/**
 * 会前事项对比通报 Markdown 生成器（OpenClaw 主路径 / LLM 可选降级）。
 */
public interface ComparisonReportGenerator {

    /**
     * 将飞书资料快照与纪要列表生成对比报告 Markdown。
     *
     * @param jobId   定时 Job ID（OpenClaw taskId 与会话隔离用）
     * @param sources 飞书资料快照
     * @param minutes 会议纪要快照
     * @return Markdown 正文；失败时返回 {@code null}
     */
    String generate(long jobId, List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes);
}
