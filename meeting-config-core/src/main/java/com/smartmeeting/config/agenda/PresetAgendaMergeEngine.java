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
            if (hostAgendaItemHasFeishu(item)) {
                continue;
            }
            HostAgendaItem fromTemplate = HostAgendaJsonCodec.parseItemAtIndex(
                    new ObjectMapper(), presetHostAgendaJson, i);
            if (fromTemplate != null && hostAgendaItemHasFeishu(fromTemplate)) {
                copyHostAgendaFields(fromTemplate, item);
            } else {
                List<AgendaDocBindingSnapshot> cfgs = byAgenda.get(i);
                if (cfgs != null && !cfgs.isEmpty()) {
                    item.setDocs(cfgs.stream().map(HostAgendaJsonCodec::fromSnapshot).toList());
                    syncLegacyFromDocs(item);
                }
            }
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
                    if (doc != null && AgendaDocRoleRules.isSourceRoleForMerge(
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
                if (AgendaDocRoleRules.isSourceRoleForMerge(cfg)) {
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

    public static Optional<AgendaReportBinding> findReportBinding(List<AgendaDocBindingSnapshot> allBindings,
                                                                  int presetTypeCode, int agendaIndex) {
        if (allBindings != null && !allBindings.isEmpty()) {
            AgendaDocBindingSnapshot row = allBindings.stream()
                    .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                    .filter(b -> presetTypeCode == (b.getPresetTypeCode() != null ? b.getPresetTypeCode() : 0))
                    .filter(b -> agendaIndex == (b.getAgendaIndex() != null ? b.getAgendaIndex() : -1))
                    .filter(b -> {
                        String role = b.getConfigRole();
                        if (role == null) {
                            return false;
                        }
                        role = role.trim().toUpperCase(Locale.ROOT);
                        return "OUTPUT".equals(role) || "BOTH".equals(role);
                    })
                    .max(Comparator.comparingLong(b -> b.getId() != null ? b.getId() : 0L))
                    .orElse(null);
            if (row != null) {
                String outputFeishu = null;
                if ("OUTPUT".equalsIgnoreCase(nullToEmpty(row.getConfigRole()))
                        && row.getFeishuDocUrl() != null && !row.getFeishuDocUrl().isBlank()) {
                    outputFeishu = row.getFeishuDocUrl().trim();
                }
                return Optional.of(new AgendaReportBinding(
                        row.getGeneratedReportUrl(),
                        row.getGeneratedReportAt(),
                        outputFeishu));
            }
        }
        return Optional.empty();
    }

    public static Optional<AgendaReportBinding> findReportBindingInHostAgenda(String presetHostAgendaJson,
                                                                              int agendaIndex,
                                                                              ObjectMapper mapper) {
        return HostAgendaJsonCodec.findReportBinding(presetHostAgendaJson, agendaIndex, mapper);
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
                if (doc != null && AgendaDocRoleRules.isSourceRoleForMerge(
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
