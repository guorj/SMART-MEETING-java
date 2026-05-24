package com.smartmeeting.matterprogress.minute;

import com.smartmeeting.matterprogress.model.MinuteSnapshot;

import java.util.List;

/** 纪要查询：两种 job 规则 */
public interface MeetingMinuteQuery {

    /**
     * 模板会：最近 days 天内 preset 匹配且 generation_status=READY 的纪要。
     */
    List<MinuteSnapshot> templateMinutesSinceDays(int presetTypeCode, int days);

    /**
     * 非模板会：按 meeting_id 列表查询。
     */
    List<MinuteSnapshot> byMeetingIds(List<String> meetingIds);
}
