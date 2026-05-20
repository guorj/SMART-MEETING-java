package com.smartmeeting.service.host;

import java.util.regex.Pattern;

/**
 * 点名答到肯定语匹配器（单麦、无声纹场景）。
 *
 * <p>判断 ASR 定稿文本是否可视为参会人「答到」类肯定回复，供
 * {@link MeetingHostSessionService} 在答到窗口内自动推进流程。
 * 基于话术规则做流程信任，不校验说话人身份。
 *
 * <p>需排除主持 TTS「请某某答到」回声、追问句（含「吗」）及代他人陈述等误判。
 */
public final class RollCallAffirmationMatcher {

    /** 与主持话术 {@code 请X答到} 一致时视为播报回声，不计入用户答到（窗口内仍可能拾音到刚播完的点名）。 */
    private static final Pattern LIKELY_NAME_CALL_TTS =
            Pattern.compile("^请[^，。\\s]{0,48}答到[。！!…\\s]*$");

    /**
     * 短句「到了」类：仅允许句首为语气词/标点，避免「他到了吗」「李海天到了吗」「他到了」等追问或代他人陈述被判答到。
     */
    private static final Pattern SHORT_SELF_DAOD_LE =
            Pattern.compile("^[\\s，,。.…、嗯啊哦噢喔哎好啦呀哇吧呗得了呃哼]*到了[呢呐啊呀哇啦吧噢哦]?[。！!…\\s]*$");

    private RollCallAffirmationMatcher() {
    }

    /**
     * 判断文本是否像追问或核实，不应视为本人答到。
     */
    static boolean looksLikeQuestionOrInquiry(String t) {
        if (t == null || t.isEmpty()) {
            return false;
        }
        if (t.indexOf('吗') >= 0 || t.indexOf('？') >= 0 || t.indexOf('?') >= 0) {
            return true;
        }
        return t.contains("到没")
                || t.contains("没到")
                || t.contains("有没有")
                || t.contains("是不是")
                || t.contains("为啥")
                || t.contains("为什么")
                || t.contains("是否");
    }

    /**
     * 判断原始 ASR 文本是否匹配答到类肯定语。
     *
     * <p>规则概要：排除追问；含「答到」时排除整句 TTS 点名且长度不超过 200；
     * 短句「到了」需符合 {@link #SHORT_SELF_DAOD_LE}；极短紧凑句匹配「在」「收到」等词表。
     *
     * @param raw ASR 原文，可为 null
     * @return 视为答到肯定时为 true
     */
    public static boolean matches(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (looksLikeQuestionOrInquiry(t)) {
            return false;
        }
        // 显式「答到」：不因 ASR 粘连长句（含环境/TTS 尾音）被 48 字上限误杀；排除整句仅为「请某某答到」的播报。
        if (t.contains("答到")) {
            if (LIKELY_NAME_CALL_TTS.matcher(t).matches()) {
                return false;
            }
            return t.length() <= 200;
        }
        if (t.length() > 48) {
            return false;
        }
        if (t.contains("到了") && t.length() <= 16 && SHORT_SELF_DAOD_LE.matcher(t).matches()) {
            return true;
        }
        String compact = t
                .replace("。", "")
                .replace("！", "")
                .replace("!", "")
                .replace("，", "")
                .replace(",", "")
                .replace("？", "")
                .replace("?", "")
                .replace("、", "")
                .replace(" ", "");
        if (compact.length() <= 8) {
            return "到".equals(compact)
                    || "在".equals(compact)
                    || "有".equals(compact)
                    || "在的".equals(compact)
                    || "是的".equals(compact)
                    || "到啦".equals(compact)
                    || "我在".equals(compact)
                    || "我到了".equals(compact)
                    || "在呢".equals(compact)
                    || "收到".equals(compact)
                    || "好的".equals(compact)
                    || "嗯".equals(compact);
        }
        return false;
    }
}
