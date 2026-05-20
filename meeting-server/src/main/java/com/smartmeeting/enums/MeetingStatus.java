package com.smartmeeting.enums;

/**
 * 会议生命周期状态枚举。
 * <p>
 * 持久化于 {@code int_meeting.status}，各常量附带中文描述便于前端展示。
 */
public enum MeetingStatus {
    /** 议题收集中 */
    ISSUE_COLLECTING("议题收集中"),
    /** 已邀约参会人 */
    INVITED("已邀约"),
    /** 会议进行中 */
    STARTED("进行中"),
    /** 进度通报环节 */
    REVIEWING("进度通报中"),
    /** 录音采集中 */
    RECORDING("录音中"),
    /** 会后处理中（转写/纪要生成等） */
    PROCESSING("处理中"),
    /** 会议已完成 */
    COMPLETED("已完成"),
    /** 待办跟踪阶段 */
    TODO_TRACKING("待办跟踪中"),
    /** 会议已暂停 */
    PAUSED("已暂停"),
    /** 会议已取消 */
    CANCELLED("已取消"),
    /** 全部待办已完成 */
    ALL_DONE("全部完成"),
    /** 会议已归档 */
    ARCHIVED("已归档"),
    /** 会议异常中止 */
    ABORTED("已中止"),
    /** 会议已关闭 */
    CLOSED("已关闭");

    /** 中文展示描述 */
    private final String description;

    MeetingStatus(String description) { this.description = description; }

    /**
     * 获取状态的中文描述。
     *
     * @return 中文描述文本
     */
    public String getDescription() { return description; }
}
