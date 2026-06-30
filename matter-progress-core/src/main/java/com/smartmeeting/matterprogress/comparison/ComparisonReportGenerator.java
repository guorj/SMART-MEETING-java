package com.smartmeeting.matterprogress.comparison;

import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.SourceDataSnapshot;

import java.util.List;

/** 对比报告生成（Legacy 路径）。返回结构化事项列表。 */
public interface ComparisonReportGenerator {

    /**
     * @param jobId   任务 ID
     * @param sources oabp SOURCE 快照
     * @param minutes 纪要快照
     * @return 解析结果（success=false 表示整体失败 → run FAILED）
     */
    ParsedComparisonItems generate(long jobId, List<SourceDataSnapshot> sources, List<MinuteSnapshot> minutes);
}
