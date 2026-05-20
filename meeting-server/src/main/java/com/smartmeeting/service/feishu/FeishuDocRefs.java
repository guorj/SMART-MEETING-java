package com.smartmeeting.service.feishu;

import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.entity.MatterProgressDocConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会序下多条飞书资料引用的合并、去重与 DTO 转换工具类。
 *
 * <p>仅以 URL 为配置源，通过 {@link FeishuResourceResolver} 解析为 {@link FeishuResourceRef}，
 * 再按资源类型与 URL/token 生成去重键，供主持页等场景展示多条不重复资料。
 */
public final class FeishuDocRefs {

    private FeishuDocRefs() {
    }

    /**
     * 将内部资源引用转为 API 用 DTO。
     *
     * @param ref 飞书资源引用，可为 null
     * @return 含 kind 与打开 URL 的 DTO；ref 为 null 时返回 null
     */
    public static FeishuDocRefDto toDto(FeishuResourceRef ref) {
        if (ref == null) {
            return null;
        }
        String open = ref.defaultOpenUrl();
        return FeishuDocRefDto.builder()
                .kind(ref.kind().name())
                .url(open != null ? open : "")
                .build();
    }

    /**
     * 合并引用列表并按去重键去重，仅保留需在主持页展示的项。
     *
     * @param refs 原始引用列表，可为 null 或空
     * @return 去重后的新列表；输入为空时返回不可变空列表
     */
    public static List<FeishuResourceRef> mergeDistinct(List<FeishuResourceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<FeishuResourceRef> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FeishuResourceRef ref : refs) {
            if (ref == null || !ref.showOnHostPage()) {
                continue;
            }
            String key = dedupeKey(ref);
            if (seen.add(key)) {
                out.add(ref);
            }
        }
        return out;
    }

    /**
     * 解析 URL 后追加到目标列表（若未重复且需在主持页展示）。
     *
     * @param target 可变目标列表，非 null
     * @param url      飞书 HTTPS 链接，无效或不应展示时忽略
     */
    public static void addFromUrl(List<FeishuResourceRef> target, String url) {
        FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
        if (ref == null || !ref.showOnHostPage()) {
            return;
        }
        String key = dedupeKey(ref);
        for (FeishuResourceRef existing : target) {
            if (dedupeKey(existing).equals(key)) {
                return;
            }
        }
        target.add(ref);
    }

    /**
     * 从事项进度文档配置中的 URL 追加引用。
     *
     * @param target 可变目标列表
     * @param cfg    事项进度配置，为 null 时不处理
     */
    public static void addFromConfig(List<FeishuResourceRef> target, MatterProgressDocConfig cfg) {
        if (cfg == null) {
            return;
        }
        addFromUrl(target, cfg.getFeishuDocUrl());
    }

    /**
     * 从已有 DTO 的 URL 追加引用。
     *
     * @param target 可变目标列表
     * @param dto    飞书资料 DTO，为 null 时不处理
     */
    public static void addFromDto(List<FeishuResourceRef> target, FeishuDocRefDto dto) {
        if (dto == null) {
            return;
        }
        addFromUrl(target, dto.getUrl());
    }

    /**
     * 返回资源类型在 UI 上的中文展示标签。
     *
     * @param kind 飞书资源类型，可为 null
     * @return 如「云文档」「知识库」；未知或 null 时返回「飞书资料」
     */
    public static String kindLabel(FeishuResourceKind kind) {
        if (kind == null) {
            return "飞书资料";
        }
        return switch (kind) {
            case WIKI -> "知识库";
            case BASE -> "多维表格";
            case DOCX -> "云文档";
            default -> "飞书资料";
        };
    }

    /** 生成用于列表去重的键（优先 URL，否则 kind+token+tableId）。 */
    private static String dedupeKey(FeishuResourceRef ref) {
        String url = ref.sourceUrl() != null ? ref.sourceUrl().trim() : "";
        if (!url.isEmpty()) {
            return ref.kind().name() + "|" + url;
        }
        String token = ref.primaryToken() != null ? ref.primaryToken().trim() : "";
        String table = ref.tableId() != null ? ref.tableId().trim() : "";
        return ref.kind().name() + "|t|" + token + "|" + table;
    }
}
