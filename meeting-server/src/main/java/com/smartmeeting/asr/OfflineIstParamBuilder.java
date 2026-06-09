package com.smartmeeting.asr;

import com.smartmeeting.config.system.ConfigValueClamp;
import com.smartmeeting.config.MeetingAsrProperties;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 根据配置与参会人声纹，解析 IST upload 的 roleType / roleNum / featureIds。
 */
public final class OfflineIstParamBuilder {

    private OfflineIstParamBuilder() {
    }

    public static OfflineIstOptions resolve(MeetingAsrProperties asrProperties,
                                            List<String> featureIds,
                                            int participantCount) {
        if (asrProperties == null || !asrProperties.isOfflineRoleEnabled()) {
            return OfflineIstOptions.disabled();
        }

        int roleNum = resolveRoleNum(asrProperties, participantCount);
        int maxFeatureIds = Math.max(1, asrProperties.getOfflineIstMaxFeatureIds());
        List<String> cleaned = cleanFeatureIds(featureIds, maxFeatureIds);
        String mode = normalizeMode(asrProperties.getOfflineRoleMode());

        return switch (mode) {
            case "blind" -> OfflineIstOptions.blind(roleNum);
            case "voiceprint" -> resolveVoiceprint(roleNum, cleaned, true);
            default -> cleaned.size() >= 2
                    ? resolveVoiceprint(roleNum, cleaned, false)
                    : OfflineIstOptions.blind(roleNum);
        };
    }

    static int resolveRoleNum(MeetingAsrProperties asrProperties, int participantCount) {
        if (asrProperties == null || !asrProperties.isOfflineRoleNumHintEnabled()) {
            return 0;
        }
        if (participantCount <= 0) {
            return 0;
        }
        int cap = Math.max(0, asrProperties.getOfflineIstMaxRoleNum());
        return ConfigValueClamp.clampInt(participantCount, 0, cap);
    }

    static List<String> cleanFeatureIds(List<String> featureIds, int maxFeatureIds) {
        if (featureIds == null || featureIds.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxFeatureIds);
        return featureIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    static String joinFeatureIds(List<String> featureIds) {
        if (featureIds == null || featureIds.isEmpty()) {
            return null;
        }
        return String.join(",", featureIds);
    }

    private static OfflineIstOptions resolveVoiceprint(int roleNum, List<String> cleaned, boolean strictVoiceprint) {
        if (cleaned.size() < 2) {
            if (strictVoiceprint) {
                // voiceprint 模式无足够声纹时回退盲分
            }
            return OfflineIstOptions.blind(roleNum);
        }
        return OfflineIstOptions.voiceprint(roleNum, joinFeatureIds(cleaned));
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "auto";
        }
        return mode.trim().toLowerCase();
    }
}
