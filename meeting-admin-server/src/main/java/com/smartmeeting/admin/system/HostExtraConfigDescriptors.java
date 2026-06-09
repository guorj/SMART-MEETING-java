package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

@Component
class HostRollCallWindowSecondsDescriptor extends AbstractIntegerDescriptor {
    HostRollCallWindowSecondsDescriptor() {
        super("meeting.host.roll-call.window-seconds", "host", "12",
                "检点应答窗口时长（秒）", "meeting.host.roll-call-enabled");
    }
}

@Component
class HostRollCallAsrGraceSecondsDescriptor extends AbstractIntegerDescriptor {
    HostRollCallAsrGraceSecondsDescriptor() {
        super("meeting.host.roll-call.asr-grace-seconds", "host", "6",
                "检点窗口结束后 ASR 定稿宽限（秒）", "meeting.host.roll-call-enabled");
    }
}

@Component
class HostRollCallOnlineInventorySecondsDescriptor extends AbstractIntegerDescriptor {
    HostRollCallOnlineInventorySecondsDescriptor() {
        super("meeting.host.roll-call.online-inventory-seconds", "host", "60",
                "检点前在线人员清单展示时长（秒）", "meeting.host.roll-call-enabled");
    }
}

@Component
class HostTopicTimeoutStrategyDescriptor extends AbstractStringDescriptor {
    HostTopicTimeoutStrategyDescriptor() {
        super("meeting.host.topic-timeout-strategy", "host", "REMIND_ONLY",
                "议题超时策略（REMIND_ONLY|AUTO_NEXT|WAIT_DECISION）", "meeting.host.agenda-enabled");
    }
}

@Component
class HostReminderTopicMinutesDescriptor extends AbstractIntegerDescriptor {
    HostReminderTopicMinutesDescriptor() {
        super("meeting.host.reminder.topic-minutes-left", "host", "1",
                "议题剩余 N 分钟时 toast 提醒", "meeting.host.enabled");
    }
}

@Component
class HostReminderMeetingMinutesDescriptor extends AbstractStringDescriptor {
    HostReminderMeetingMinutesDescriptor() {
        super("meeting.host.reminder.meeting-minutes-left", "host", "10,3",
                "整场剩余分钟提醒列表（逗号分隔）", "meeting.host.enabled");
    }
}
