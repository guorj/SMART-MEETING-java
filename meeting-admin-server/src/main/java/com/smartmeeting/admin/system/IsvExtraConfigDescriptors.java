package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigJsonValidators;
import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Predicate;

@Component
class IsvSearchTopKMaxDescriptor extends AbstractIntegerDescriptor {
    IsvSearchTopKMaxDescriptor() {
        super("meeting.isv.search-top-k-max", "asr", "10",
                "ISV 1:N searchFea topK 上限", "meeting.isv.enabled", 1, 10,
                "1–10（讯飞 API 上限）");
    }
}

@Component
class IsvSearchTopKMinDescriptor extends AbstractIntegerDescriptor {
    IsvSearchTopKMinDescriptor() {
        super("meeting.isv.search-top-k-min", "asr", "3",
                "ISV 1:N topK 钳制下限", "meeting.isv.enabled", 1, 10);
    }
}

@Component
class IsvMinSliceBytesDescriptor extends AbstractIntegerDescriptor {
    IsvMinSliceBytesDescriptor() {
        super("meeting.isv.min-slice-bytes", "asr", "1600",
                "ISV 比对 PCM 最小字节数", "meeting.isv.enabled", 800, 32000);
    }
}

@Component
class IsvMinSegmentMsDescriptor extends AbstractIntegerDescriptor {
    IsvMinSegmentMsDescriptor() {
        super("meeting.isv.min-segment-ms-for-slice", "asr", "500",
                "参与 ISV 切片的句段最小时长（毫秒）", "meeting.isv.enabled", 100, 5000);
    }
}

@Component
class AsrOfflineIstMaxRoleNumDescriptor extends AbstractIntegerDescriptor {
    AsrOfflineIstMaxRoleNumDescriptor() {
        super("meeting.asr.offline-ist-max-role-num", "asr", "10",
                "IST upload roleNum 上限", "meeting.asr.offline-enabled", 0, 10);
    }
}

@Component
class AsrOfflineIstMaxFeatureIdsDescriptor extends AbstractIntegerDescriptor {
    AsrOfflineIstMaxFeatureIdsDescriptor() {
        super("meeting.asr.offline-ist-max-feature-ids", "asr", "64",
                "IST upload featureIds 个数上限", "meeting.asr.offline-enabled", 1, 64);
    }
}

@Component
class AsrOfflinePollMaxRetriesDescriptor extends AbstractIntegerDescriptor {
    AsrOfflinePollMaxRetriesDescriptor() {
        super("meeting.asr.offline-poll-max-retries", "asr", "60",
                "离线 IST 轮询最大次数", "meeting.asr.offline-enabled", 10, 120);
    }
}

@Component
class AsrOfflinePollIntervalMsDescriptor extends AbstractIntegerDescriptor {
    AsrOfflinePollIntervalMsDescriptor() {
        super("meeting.asr.offline-poll-interval-ms", "asr", "5000",
                "离线 IST 轮询间隔（毫秒）", "meeting.asr.offline-enabled", 1000, 30000);
    }
}

@Component
class IsvMatchScoreThresholdRangedDescriptor implements SystemConfigDescriptor {
    @Override
    public String key() {
        return "meeting.isv.match-score-threshold";
    }

    @Override
    public String category() {
        return "asr";
    }

    @Override
    public ConfigValueType type() {
        return ConfigValueType.STRING;
    }

    @Override
    public String defaultValue() {
        return "0.6";
    }

    @Override
    public boolean hotReloadable() {
        return true;
    }

    @Override
    public String description() {
        return "1:N 匹配最低置信度";
    }

    @Override
    public Optional<String> parentKey() {
        return Optional.of("meeting.isv.enabled");
    }

    @Override
    public OptionalDouble doubleMin() {
        return OptionalDouble.of(0.0);
    }

    @Override
    public OptionalDouble doubleMax() {
        return OptionalDouble.of(1.0);
    }

    @Override
    public String valueRangeHint() {
        return "0.0–1.0";
    }

    @Override
    public Optional<Predicate<String>> validator() {
        return Optional.of(ConfigJsonValidators.doubleInRange(0.0, 1.0));
    }
}
