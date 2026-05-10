package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 吉青汽车科技集团等固定会务类型（1-5），存库可维护；6「其他」不入库。
 */
@Data
@TableName("int_meeting_type_preset")
public class MeetingTypePreset {
    @TableId
    private Integer code;
    private String displayName;
    private String company;
    private String department;
    private String groupName;
    private String scheduleNote;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    /** 中文顿号、逗号或「及」连接的与会人名单 */
    private String participantsNames;
}
