package com.smartmeeting.asr;

/**
 * 讯飞 IST v2 上传时的说话人分离参数。
 *
 * @param roleType      0=关，1=盲分，3=声纹分离
 * @param roleNum       说话人数 hint，0=盲分
 * @param featureIdsCsv roleType=3 时逗号拼接的声纹 ID（可为 null）
 */
public record OfflineIstOptions(int roleType, int roleNum, String featureIdsCsv) {

    public static OfflineIstOptions disabled() {
        return new OfflineIstOptions(0, 0, null);
    }

    public static OfflineIstOptions blind(int roleNum) {
        return new OfflineIstOptions(1, roleNum, null);
    }

    public static OfflineIstOptions voiceprint(int roleNum, String featureIdsCsv) {
        return new OfflineIstOptions(3, roleNum, featureIdsCsv);
    }
}
