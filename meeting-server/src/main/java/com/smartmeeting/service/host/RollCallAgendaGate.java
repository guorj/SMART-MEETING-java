package com.smartmeeting.service.host;

import java.util.Collection;

/**
 * 检点议程门控：主持实际会序中是否配置了「会议检点」类环节（标题含「检点」）。
 */
public final class RollCallAgendaGate {

    private RollCallAgendaGate() {
    }

    /**
     * @param topicTitles 会序标题列表，可为 null
     * @return 任一标题包含「检点」时为 true
     */
    public static boolean agendaHasRollCallChapter(Collection<String> topicTitles) {
        if (topicTitles == null || topicTitles.isEmpty()) {
            return false;
        }
        for (String title : topicTitles) {
            if (title != null && title.contains("检点")) {
                return true;
            }
        }
        return false;
    }
}
