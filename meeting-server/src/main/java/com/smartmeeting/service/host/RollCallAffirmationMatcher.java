package com.smartmeeting.service.host;

import java.util.regex.Pattern;

/**
 * 单麦、无声纹：定稿语音是否视为「答到」类肯定（流程信任，不校验说话人身份）。
 */
public final class RollCallAffirmationMatcher {

    /** 与主持话术 {@code 请X答到} 一致时视为播报回声，不计入用户答到（窗口内仍可能拾音到刚播完的点名）。 */
    private static final Pattern LIKELY_NAME_CALL_TTS =
            Pattern.compile("^请[^，。\\s]{0,48}答到[。！!…\\s]*$");

    private RollCallAffirmationMatcher() {
    }

    public static boolean matches(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
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
        if (t.contains("到了") && t.length() <= 16) {
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
