package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

@Component
class VoiceprintOfflineMinSliceMsDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineMinSliceMsDescriptor() {
        super("meeting.voiceprint.offline-min-slice-ms", "asr", "3000",
                "ISV 代表切片最小时长（毫秒）", "meeting.voiceprint.offline-label-enabled",
                1000, 30000);
    }
}

@Component
class VoiceprintOfflineMaxSliceMsDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineMaxSliceMsDescriptor() {
        super("meeting.voiceprint.offline-max-slice-ms", "asr", "10000",
                "ISV 单次切片最大时长（毫秒）", "meeting.voiceprint.offline-label-enabled",
                3000, 60000);
    }
}

@Component
class VoiceprintOfflineMaxSpeakersDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineMaxSpeakersDescriptor() {
        super("meeting.voiceprint.offline-max-speakers", "asr", "8",
                "单场最多 ISV 处理的 IST 簇数", "meeting.voiceprint.offline-label-enabled",
                1, 20);
    }
}

@Component
class VoiceprintOfflineVoteSlicesDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineVoteSlicesDescriptor() {
        super("meeting.voiceprint.offline-vote-slices", "asr", "3",
                "簇级 ISV 投票每簇最长句段数", "meeting.voiceprint.offline-label-enabled",
                1, 10);
    }
}

@Component
class VoiceprintOfflineSegmentRelabelDescriptor extends AbstractBooleanDescriptor {
    VoiceprintOfflineSegmentRelabelDescriptor() {
        super("meeting.voiceprint.offline-segment-relabel-enabled", "asr", "true",
                "未命名簇是否按句段逐段 ISV 补标", "meeting.voiceprint.offline-label-enabled");
    }
}

@Component
class VoiceprintOfflineSplitClusterDescriptor extends AbstractBooleanDescriptor {
    VoiceprintOfflineSplitClusterDescriptor() {
        super("meeting.voiceprint.offline-split-cluster-enabled", "asr", "true",
                "同 IST 簇内多 feature 是否允许分裂", "meeting.voiceprint.offline-label-enabled");
    }
}

@Component
class VoiceprintOfflineSplitMinSegmentsDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineSplitMinSegmentsDescriptor() {
        super("meeting.voiceprint.offline-split-min-segments", "asr", "2",
                "簇内分裂：独立 feature 至少命中句段数", "meeting.voiceprint.offline-label-enabled",
                1, 10);
    }
}

@Component
class VoiceprintOfflineMinSliceFloorMsDescriptor extends AbstractIntegerDescriptor {
    VoiceprintOfflineMinSliceFloorMsDescriptor() {
        super("meeting.voiceprint.offline-min-slice-floor-ms", "asr", "1000",
                "切片时长下限保护（毫秒）", "meeting.voiceprint.offline-label-enabled",
                500, 10000);
    }
}
