package com.smartmeeting.config.feishu;

import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.HostAgendaFeishuDocRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FeishuDocRefs {

    private FeishuDocRefs() {
    }

    public static HostAgendaFeishuDocRef toHostRef(FeishuResourceRef ref) {
        if (ref == null) {
            return null;
        }
        String open = ref.defaultOpenUrl();
        return HostAgendaFeishuDocRef.builder()
                .kind(ref.kind().name())
                .url(open != null ? open : "")
                .build();
    }

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

    public static void addFromBinding(List<FeishuResourceRef> target, AgendaDocBindingSnapshot cfg) {
        if (cfg == null) {
            return;
        }
        FeishuResourceRef ref = FeishuResourceResolver.resolve(cfg);
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

    public static void addFromHostRef(List<FeishuResourceRef> target, HostAgendaFeishuDocRef dto) {
        if (dto == null) {
            return;
        }
        addFromUrl(target, dto.getUrl());
    }

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
