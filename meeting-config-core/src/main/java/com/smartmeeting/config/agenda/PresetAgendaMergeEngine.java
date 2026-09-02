package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.feishu.FeishuDocRefs;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 预设会序与内嵌资料合并引擎（无 DB / Redis / 飞书 HTTP）。
 * <p>
 * 资料权威存储为 {@code int_meeting_type_preset.host_agenda} v2 {@code items[].docs[]}，
 * 由 {@link HostAgendaJsonCodec} 编解码。
 */
public final class PresetAgendaMergeEngine {

    private PresetAgendaMergeEngine() {
    }

    public static String syncHostAgendaJson(ObjectMapper objectMapper, int presetTypeCode,
                                            String presetHostAgendaJson,
                                            List<HostAgendaItem> requestItems,
                                            List<AgendaDocBindingSnapshot> sourceBindings) {
        if (presetTypeCode <= 0) {
            return HostAgendaJsonCodec.toJson(objectMapper, requestItems);
        }
        List<HostAgendaItem> items = copyHostAgendaItems(requestItems);
        if (items.isEmpty() && presetHostAgendaJson != null && !presetHostAgendaJson.isBlank()) {
            items = new ArrayList<>(HostAgendaJsonCodec.parseItems(objectMapper, presetHostAgendaJson));
        }
        if (items.isEmpty()) {
            return null;
        }
        enrichHostAgendaItems(presetTypeCode, presetHostAgendaJson, items, sourceBindings);
        return HostAgendaJsonCodec.toJson(objectMapper, items);
    }

    public static String toHostAgendaJson(ObjectMapper objectMapper, List<HostAgendaItem> items) {
        return HostAgendaJsonCodec.toJson(objectMapper, items);
    }

    public static void enrichHostAgendaItems(int presetTypeCode, String presetHostAgendaJson,
                                             List<HostAgendaItem> items,
                                             List<AgendaDocBindingSnapshot> sourceBindings) {
        if (items == null || items.isEmpty() || presetTypeCode <= 0) {
            return;
        }
        List<AgendaDocBindingSnapshot> enabled = filterSourceBindings(sourceBindings);
        Map<Integer, List<AgendaDocBindingSnapshot>> byAgenda = groupConfigsByAgenda(enabled);
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            if (item == null) {
                continue;
            }
            HostAgendaItem fromTemplate = HostAgendaJsonCodec.parseItemAtIndex(
                    new ObjectMapper(), presetHostAgendaJson, i);
            if (hostAgendaItemHasFeishu(item)) {
                // 已有飞书资料：不覆盖资料绑定，仅补齐缺失的 oabp 字段
                fillOabpFromTemplate(item, fromTemplate);
                continue;
            }
            if (fromTemplate != null && hostAgendaItemHasFeishu(fromTemplate)) {
                copyHostAgendaFields(fromTemplate, item);
            } else {
                List<AgendaDocBindingSnapshot> cfgs = byAgenda.get(i);
                if (cfgs != null && !cfgs.isEmpty()) {
                    item.setDocs(cfgs.stream().map(HostAgendaJsonCodec::fromSnapshot).toList());
                    syncLegacyFromDocs(item);
                }
            }
            fillOabpFromTemplate(item, fromTemplate);
        }
    }

    /**
     * 当会序项未配置 oabpTaskSql 但 preset 模板项已配置时，从模板补齐 oabp 字段，
     * 使主持页在无资料绑定时也能展示项目任务表格。
     */
    private static void fillOabpFromTemplate(HostAgendaItem item, HostAgendaItem fromTemplate) {
        if (item == null || fromTemplate == null) {
            return;
        }
        if (item.getOabpTaskSql() == null || item.getOabpTaskSql().isBlank()) {
            if (fromTemplate.getOabpTaskSql() != null && !fromTemplate.getOabpTaskSql().isBlank()) {
                item.setOabpTaskSql(fromTemplate.getOabpTaskSql().strip());
            }
        }
        if (item.getOabpTaskShow() == null && fromTemplate.getOabpTaskShow() != null) {
            item.setOabpTaskShow(fromTemplate.getOabpTaskShow());
        }
        if ((item.getOabpDisplayTemplate() == null || item.getOabpDisplayTemplate().isEmpty())
                && fromTemplate.getOabpDisplayTemplate() != null && !fromTemplate.getOabpDisplayTemplate().isEmpty()) {
            item.setOabpDisplayTemplate(fromTemplate.getOabpDisplayTemplate());
        }
        if ((item.getOabpSqlPresetId() == null || item.getOabpSqlPresetId().isBlank())
                && fromTemplate.getOabpSqlPresetId() != null && !fromTemplate.getOabpSqlPresetId().isBlank()) {
            item.setOabpSqlPresetId(fromTemplate.getOabpSqlPresetId().trim());
        }
        if (item.getOabpTaskSqlStrict() == null && fromTemplate.getOabpTaskSqlStrict() != null) {
            item.setOabpTaskSqlStrict(fromTemplate.getOabpTaskSqlStrict());
        }
    }

    public static List<FeishuResourceRef> resolveAllResources(String meetingHostAgendaJson,
                                                              Integer presetTypeCode,
                                                              String presetHostAgendaJson,
                                                              List<AgendaDocBindingSnapshot> agendaBindings,
                                                              int agendaIndex,
                                                              List<HostAgendaFeishuDocRef> runtimeDocs) {
        ObjectMapper mapper = new ObjectMapper();
        List<FeishuResourceRef> refs = new ArrayList<>();
        if (runtimeDocs != null) {
            for (HostAgendaFeishuDocRef dto : runtimeDocs) {
                FeishuDocRefs.addFromHostRef(refs, dto);
            }
        }
        if (meetingHostAgendaJson != null && !meetingHostAgendaJson.isBlank()) {
            HostAgendaItem fromMeeting = HostAgendaJsonCodec.parseItemAtIndex(mapper, meetingHostAgendaJson, agendaIndex);
            if (hostAgendaItemHasFeishu(fromMeeting)) {
                addHostAgendaItemFeishuRefs(refs, fromMeeting);
                return FeishuDocRefs.mergeDistinct(refs);
            }
        }
        if (presetTypeCode != null && presetTypeCode > 0) {
            HostAgendaItem fromTemplate = HostAgendaJsonCodec.parseItemAtIndex(mapper, presetHostAgendaJson, agendaIndex);
            if (fromTemplate != null && fromTemplate.getDocs() != null) {
                for (HostAgendaDocBinding doc : fromTemplate.getDocs()) {
                    if (doc != null && doc.isShowInHost() && AgendaDocRoleRules.isSourceRoleForMerge(
                            HostAgendaJsonCodec.toSnapshot(doc, presetTypeCode, agendaIndex))) {
                        FeishuDocRefs.addFromDocBinding(refs, doc);
                    }
                }
                if (!refs.isEmpty()) {
                    return FeishuDocRefs.mergeDistinct(refs);
                }
            }
            if (hostAgendaItemHasFeishu(fromTemplate)) {
                addHostAgendaItemFeishuRefs(refs, fromTemplate);
                return FeishuDocRefs.mergeDistinct(refs);
            }
            for (AgendaDocBindingSnapshot cfg : agendaBindings) {
                if (cfg.isShowInHost() && AgendaDocRoleRules.isSourceRoleForMerge(cfg)) {
                    FeishuDocRefs.addFromBinding(refs, cfg);
                }
            }
        }
        return FeishuDocRefs.mergeDistinct(refs);
    }

    public static boolean presetTemplateDefinesFeishuForIndex(String presetHostAgendaJson, int agendaIndex) {
        return hostAgendaItemHasFeishu(
                HostAgendaJsonCodec.parseItemAtIndex(new ObjectMapper(), presetHostAgendaJson, agendaIndex));
    }

    public static List<HostAgendaItem> parseHostAgendaItems(ObjectMapper objectMapper, String hostAgendaJson) {
        return HostAgendaJsonCodec.parseItems(objectMapper, hostAgendaJson);
    }

    public static List<AgendaDocBindingSnapshot> extractBindingsFromHostAgenda(int presetTypeCode,
                                                                                 String hostAgendaJson,
                                                                                 ObjectMapper mapper) {
        return HostAgendaJsonCodec.extractAllBindings(presetTypeCode, hostAgendaJson, mapper);
    }

    public static List<AgendaDocBindingSnapshot> filterSourceBindings(List<AgendaDocBindingSnapshot> bindings) {
        if (bindings == null) {
            return List.of();
        }
        return bindings.stream().filter(AgendaDocRoleRules::isSourceRoleForMerge).toList();
    }

    public static boolean hostAgendaItemHasFeishu(HostAgendaItem item) {
        if (item == null) {
            return false;
        }
        if (item.getDocs() != null) {
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc != null && FeishuResourceResolver.isRecognizedFeishuDocUrl(doc.getUrl())) {
                    return true;
                }
            }
        }
        if (item.getFeishuDocs() != null) {
            for (HostAgendaFeishuDocRef d : item.getFeishuDocs()) {
                if (d != null && FeishuResourceResolver.isRecognizedFeishuDocUrl(d.getUrl())) {
                    return true;
                }
            }
        }
        return FeishuResourceResolver.isRecognizedFeishuDocUrl(item.getFeishuDocUrl());
    }

    private static List<HostAgendaItem> copyHostAgendaItems(List<HostAgendaItem> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<HostAgendaItem> out = new ArrayList<>();
        for (HostAgendaItem src : source) {
            if (src == null || src.getTitle() == null || src.getTitle().isBlank()) {
                continue;
            }
            HostAgendaItem dto = new HostAgendaItem();
            dto.setTitle(src.getTitle().trim());
            dto.setMinutes(src.getMinutes() != null && src.getMinutes() > 0 ? src.getMinutes() : 10);
            if (src.getDetail() != null && !src.getDetail().isBlank()) {
                dto.setDetail(src.getDetail().trim());
            }
            if (src.getDocs() != null && !src.getDocs().isEmpty()) {
                dto.setDocs(new ArrayList<>(src.getDocs()));
                syncLegacyFromDocs(dto);
            } else if (src.getFeishuDocs() != null && !src.getFeishuDocs().isEmpty()) {
                dto.setFeishuDocs(new ArrayList<>(src.getFeishuDocs()));
                applyFirstUrlField(dto);
            } else if (src.getFeishuDocUrl() != null && !src.getFeishuDocUrl().isBlank()) {
                dto.setFeishuDocUrl(src.getFeishuDocUrl().trim());
            }
            copyOabpFields(src, dto);
            out.add(dto);
        }
        return out;
    }

    private static Map<Integer, List<AgendaDocBindingSnapshot>> groupConfigsByAgenda(
            List<AgendaDocBindingSnapshot> configs) {
        java.util.Map<Integer, List<AgendaDocBindingSnapshot>> map = new java.util.HashMap<>();
        if (configs == null) {
            return map;
        }
        for (AgendaDocBindingSnapshot c : configs) {
            if (c.getAgendaIndex() == null || c.getAgendaIndex() < 0) {
                continue;
            }
            map.computeIfAbsent(c.getAgendaIndex(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private static void applyFirstUrlField(HostAgendaItem item) {
        if (item.getFeishuDocs() == null || item.getFeishuDocs().isEmpty()) {
            return;
        }
        HostAgendaFeishuDocRef first = item.getFeishuDocs().get(0);
        if (first.getUrl() != null && !first.getUrl().isBlank()) {
            item.setFeishuDocUrl(first.getUrl());
        }
    }

    private static void addHostAgendaItemFeishuRefs(List<FeishuResourceRef> refs, HostAgendaItem item) {
        if (item.getDocs() != null) {
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc != null && doc.isShowInHost() && AgendaDocRoleRules.isSourceRoleForMerge(
                        HostAgendaJsonCodec.toSnapshot(doc, 0, 0))) {
                    FeishuDocRefs.addFromDocBinding(refs, doc);
                }
            }
        }
        if (item.getFeishuDocs() != null) {
            for (HostAgendaFeishuDocRef dto : item.getFeishuDocs()) {
                FeishuDocRefs.addFromHostRef(refs, dto);
            }
        } else {
            FeishuDocRefs.addFromUrl(refs, item.getFeishuDocUrl());
        }
    }

    private static void copyHostAgendaFields(HostAgendaItem from, HostAgendaItem to) {
        if (from.getDocs() != null && !from.getDocs().isEmpty()) {
            to.setDocs(new ArrayList<>(from.getDocs()));
            syncLegacyFromDocs(to);
        } else if (from.getFeishuDocs() != null && !from.getFeishuDocs().isEmpty()) {
            to.setFeishuDocs(new ArrayList<>(from.getFeishuDocs()));
            applyFirstUrlField(to);
        } else if (from.getFeishuDocUrl() != null && !from.getFeishuDocUrl().isBlank()) {
            to.setFeishuDocUrl(from.getFeishuDocUrl().trim());
        }
        copyOabpFields(from, to);
        copyExternalLinkFields(from, to);
    }

    /**
     * 复制外链字段（与 {@link HostAgendaJsonCodec#toJson} 序列化字段对齐），
     * 确保会议快照合并/刷新时不会丢失 externalUrl / externalLinkLabel。
     */
    private static void copyExternalLinkFields(HostAgendaItem from, HostAgendaItem to) {
        if (from == null || to == null) {
            return;
        }
        if (from.getExternalUrl() != null && !from.getExternalUrl().isBlank()) {
            to.setExternalUrl(from.getExternalUrl().trim());
        }
        if (from.getExternalLinkLabel() != null && !from.getExternalLinkLabel().isBlank()) {
            to.setExternalLinkLabel(from.getExternalLinkLabel().trim());
        }
    }

    /**
     * 复制 oabp 相关字段（与 {@link HostAgendaJsonCodec#toJson} 序列化字段对齐），
     * 确保会议快照合并/刷新时不会丢失 oabpTaskSql / oabpTaskShow / oabpDisplayTemplate / oabpSqlPresetId。
     */
    private static void copyOabpFields(HostAgendaItem from, HostAgendaItem to) {
        if (from == null || to == null) {
            return;
        }
        if (from.getOabpTaskSql() != null && !from.getOabpTaskSql().isBlank()) {
            to.setOabpTaskSql(from.getOabpTaskSql().strip());
        }
        if (from.getOabpTaskShow() != null) {
            to.setOabpTaskShow(from.getOabpTaskShow());
        }
        if (from.getOabpDisplayTemplate() != null && !from.getOabpDisplayTemplate().isEmpty()) {
            to.setOabpDisplayTemplate(from.getOabpDisplayTemplate());
        }
        if (from.getOabpSqlPresetId() != null && !from.getOabpSqlPresetId().isBlank()) {
            to.setOabpSqlPresetId(from.getOabpSqlPresetId().trim());
        }
        if (from.getOabpTaskSqlStrict() != null) {
            to.setOabpTaskSqlStrict(from.getOabpTaskSqlStrict());
        }
    }

    private static void syncLegacyFromDocs(HostAgendaItem item) {
        if (item.getDocs() == null || item.getDocs().isEmpty()) {
            return;
        }
        List<HostAgendaFeishuDocRef> refs = new ArrayList<>();
        for (HostAgendaDocBinding doc : item.getDocs()) {
            if (doc == null || doc.getUrl() == null || doc.getUrl().isBlank()) {
                continue;
            }
            refs.add(HostAgendaFeishuDocRef.builder().url(doc.getUrl().trim()).build());
        }
        if (!refs.isEmpty()) {
            item.setFeishuDocs(refs);
            item.setFeishuDocUrl(refs.get(0).getUrl());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
