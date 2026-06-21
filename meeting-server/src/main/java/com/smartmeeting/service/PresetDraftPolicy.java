package com.smartmeeting.service;

/**
 * 会务模板草稿策略：默认每用户每模板最多 1 场未开始草稿；模板 99 例外可无限新建。
 */
public final class PresetDraftPolicy {

    /** 测试/特殊模板：不限制未开始草稿数量，快速开始每次均新建 */
    public static final int UNLIMITED_DRAFT_PRESET_CODE = 99;

    private PresetDraftPolicy() {
    }

    /**
     * 是否为可无限草稿的模板。
     *
     * @param presetTypeCode 会务类型编号
     * @return 模板 99 时为 true
     */
    public static boolean isUnlimitedDraftPreset(Integer presetTypeCode) {
        return presetTypeCode != null && presetTypeCode == UNLIMITED_DRAFT_PRESET_CODE;
    }

    /**
     * 建会前是否应复用已有单草稿（每模板 1 草稿策略）。
     *
     * @param presetTypeCode 会务类型编号
     * @return 正整数且非 99 时为 true
     */
    public static boolean shouldReuseSingleDraft(Integer presetTypeCode) {
        return presetTypeCode != null && presetTypeCode > 0 && !isUnlimitedDraftPreset(presetTypeCode);
    }
}
