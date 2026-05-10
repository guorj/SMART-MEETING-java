package com.smartmeeting.enums;

public enum MeetingStatus {
    ISSUE_COLLECTING("议题收集中"),
    INVITED("已邀约"),
    STARTED("进行中"),
    REVIEWING("进度通报中"),
    RECORDING("录音中"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    TODO_TRACKING("待办跟踪中"),
    PAUSED("已暂停"),
    CANCELLED("已取消"),
    ALL_DONE("全部完成"),
    ARCHIVED("已归档"),
    ABORTED("已中止"),
    CLOSED("已关闭");

    private final String description;
    MeetingStatus(String description) { this.description = description; }
    public String getDescription() { return description; }
}
