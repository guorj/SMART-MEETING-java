package com.smartmeeting.service.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 检点应到名单顺序：严格按 preset {@code participants_names} 首次出现顺序排列。
 *
 * <p>从 {@code int_meeting_participant} 加载时数据库无稳定排序，须用预设名单重排；
 * 未出现在预设中的参会人排在末尾（保持原列表相对顺序）。
 */
final class RollCallOrderHelper {

    private RollCallOrderHelper() {
    }

    /**
     * 按预设姓名顺序重排列表（原地替换元素顺序）。
     *
     * @param people      自 DB 加载的名单
     * @param presetOrder 预设 {@code participants_names} 解析结果
     * @param nameFn      从元素读取姓名字段
     */
    static <T> void sortByPresetOrder(List<T> people, List<String> presetOrder, Function<T, String> nameFn) {
        if (people == null || people.isEmpty() || presetOrder == null || presetOrder.isEmpty()) {
            return;
        }
        Map<String, T> byNormName = new HashMap<>();
        for (T p : people) {
            if (p == null) {
                continue;
            }
            String key = normalizeName(nameFn.apply(p));
            if (!key.isEmpty() && !byNormName.containsKey(key)) {
                byNormName.put(key, p);
            }
        }
        List<T> ordered = new ArrayList<>(people.size());
        Set<T> placed = new HashSet<>();
        for (String presetName : presetOrder) {
            T match = byNormName.get(normalizeName(presetName));
            if (match != null && placed.add(match)) {
                ordered.add(match);
            }
        }
        for (T p : people) {
            if (p != null && placed.add(p)) {
                ordered.add(p);
            }
        }
        if (ordered.size() != people.size()) {
            return;
        }
        people.clear();
        people.addAll(ordered);
    }

    static String normalizeName(String name) {
        return name != null ? name.trim() : "";
    }
}
