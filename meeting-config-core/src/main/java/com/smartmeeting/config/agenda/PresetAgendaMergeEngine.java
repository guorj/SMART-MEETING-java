package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.feishu.FeishuDocRefs;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 预设会序与 doc 绑定合并引擎（无 DB / Redis / 飞书 HTTP）。
 * <p>
 * meeting-server 的 {@code PresetAgendaDocService}、meeting-admin 预览等应委托本类，
 * 仅由应用层负责加载 {@link AgendaDocBindingSnapshot} 与缓存。
 */
public final class PresetAgendaMergeEngine {

    private PresetAgendaMergeEngine() {
    }

    public static String syncHostAgendaJson(ObjectMapper objectMapper, int presetTypeCode,
                                            String presetHostAgendaJson,
                                            List<HostAgendaItem> requestItems,
                                            List<AgendaDocBindingSnapshot> sourceBindings) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return toHostAgendaJson(objectMapper, requestItems);
        }
        List<HostAgendaItem> items = copyHostAgendaItems(requestItems);
        if (items.isEmpty() && presetHostAgendaJson != null && !presetHostAgendaJson.isBlank()) {
            items = parseHostAgendaItems(objectMapper, presetHostAgendaJson);
        }
        if (items.isEmpty()) {
            return null;
        }
        enrichHostAgendaItems(presetTypeCode, presetHostAgendaJson, items, sourceBindings);
        return toHostAgendaJson(objectMapper, items);
    }

    public static String toHostAgendaJson(ObjectMapper objectMapper, List<HostAgendaItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            var root = objectMapper.createObjectNode();
            var arr = root.putArray("items");
            for (HostAgendaItem dto : items) {
                if (dto == null || dto.getTitle() == null || dto.getTitle().isBlank()) {
                    continue;
                }
                var n = arr.addObject();
                n.put("title", dto.getTitle().trim());
                int min = dto.getMinutes() != null && dto.getMinutes() > 0 ? dto.getMinutes() : 10;
                n.put("minutes", min);
                if (dto.getDetail() != null && !dto.getDetail().isBlank()) {
                    n.put("detail", dto.getDetail().trim());
                }
                if (dto.getFeishuDocs() != null && !dto.getFeishuDocs().isEmpty()) {
                    var docs = n.putArray("feishuDocs");
                    for (HostAgendaFeishuDocRef ref : dto.getFeishuDocs()) {
                        if (ref == null || ref.getUrl() == null || ref.getUrl().isBlank()) {
                            continue;
                        }
                        var d = docs.addObject();
                        if (ref.getKind() != null && !ref.getKind().isBlank()) {
                            d.put("kind", ref.getKind());
                        }
                        d.put("url", ref.getUrl().trim());
                    }
                } else if (dto.getFeishuDocUrl() != null && !dto.getFeishuDocUrl().isBlank()) {
                    n.put("feishuDocUrl", dto.getFeishuDocUrl().trim());
                }
            }
            if (arr.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    public static void enrichHostAgendaItems(int presetTypeCode, String presetHostAgendaJson,
                                             List<HostAgendaItem> items,
                                             List<AgendaDocBindingSnapshot> sourceBindings) {
        if (items == null || items.isEmpty() || presetTypeCode < 1 || presetTypeCode > 5) {
            return;
        }
        List<AgendaDocBindingSnapshot> enabled = filterSourceBindings(sourceBindings);
        Map<Integer, List<AgendaDocBindingSnapshot>> byAgenda = groupConfigsByAgenda(enabled);
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            if (item == null) {
                continue;
            }
            if (!hostAgendaItemHasFeishu(item)) {
                HostAgendaItem fromTemplate = itemAtIndex(presetHostAgendaJson, i);
                if (hostAgendaItemHasFeishu(fromTemplate)) {
                    copyHostAgendaFeishuFields(fromTemplate, item);
                } else {
                    List<AgendaDocBindingSnapshot> cfgs = byAgenda.get(i);
                    if (cfgs != null && !cfgs.isEmpty()) {
                        item.setFeishuDocs(bindingsToHostRefs(cfgs));
                        applyFirstUrlField(item);
                    }
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
        List<FeishuResourceRef> refs = new ArrayList<>();
        if (runtimeDocs != null) {
            for (HostAgendaFeishuDocRef dto : runtimeDocs) {
                FeishuDocRefs.addFromHostRef(refs, dto);
            }
        }
        if (meetingHostAgendaJson != null && !meetingHostAgendaJson.isBlank()) {
            HostAgendaItem fromMeeting = itemAtIndex(meetingHostAgendaJson, agendaIndex);
            if (hostAgendaItemHasFeishu(fromMeeting)) {
                addHostAgendaItemFeishuRefs(refs, fromMeeting);
                return FeishuDocRefs.mergeDistinct(refs);
            }
        }
        if (presetTypeCode != null && presetTypeCode >= 1 && presetTypeCode <= 5) {
            HostAgendaItem fromTemplate = itemAtIndex(presetHostAgendaJson, agendaIndex);
            if (hostAgendaItemHasFeishu(fromTemplate)) {
                addHostAgendaItemFeishuRefs(refs, fromTemplate);
                return FeishuDocRefs.mergeDistinct(refs);
            }
            for (AgendaDocBindingSnapshot cfg : agendaBindings) {
                if (!AgendaDocRoleRules.isSourceRoleForMerge(cfg)) {
                    continue;
                }
                FeishuDocRefs.addFromBinding(refs, cfg);
            }
        }
        return FeishuDocRefs.mergeDistinct(refs);
    }

    public static boolean presetTemplateDefinesFeishuForIndex(String presetHostAgendaJson, int agendaIndex) {
        return hostAgendaItemHasFeishu(itemAtIndex(presetHostAgendaJson, agendaIndex));
    }

    public static Optional<AgendaReportBinding> findReportBinding(List<AgendaDocBindingSnapshot> allBindings,
                                                                  int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0 || allBindings == null) {
            return Optional.empty();
        }
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
        if (row == null) {
            return Optional.empty();
        }
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

    public static List<HostAgendaItem> parseHostAgendaItems(ObjectMapper objectMapper, String hostAgendaJson) {
        List<HostAgendaItem> out = new ArrayList<>();
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return out;
            }
            for (int i = 0; i < items.size(); i++) {
                HostAgendaItem dto = itemAtIndex(hostAgendaJson, i);
                if (dto != null) {
                    out.add(dto);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
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
            if (src.getFeishuDocs() != null && !src.getFeishuDocs().isEmpty()) {
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
        Map<Integer, List<AgendaDocBindingSnapshot>> map = new HashMap<>();
        if (configs == null) {
            return map;
        }
        for (AgendaDocBindingSnapshot c : configs) {
            if (c.getAgendaIndex() == null || c.getAgendaIndex() < 0) {
                continue;
            }
            map.computeIfAbsent(c.getAgendaIndex(), k -> new ArrayList<>()).add(c);
        }
        for (List<AgendaDocBindingSnapshot> list : map.values()) {
            list.sort(Comparator
                    .comparingInt((AgendaDocBindingSnapshot c) ->
                            c.getResourceSlot() != null ? c.getResourceSlot() : 0)
                    .thenComparingLong(c -> c.getId() != null ? c.getId() : 0L));
        }
        return map;
    }

    private static List<HostAgendaFeishuDocRef> bindingsToHostRefs(List<AgendaDocBindingSnapshot> cfgs) {
        List<HostAgendaFeishuDocRef> out = new ArrayList<>();
        for (AgendaDocBindingSnapshot cfg : cfgs) {
            FeishuResourceRef ref = FeishuResourceResolver.resolve(cfg);
            HostAgendaFeishuDocRef dto = FeishuDocRefs.toHostRef(ref);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
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
        if (item.getFeishuDocs() != null) {
            for (HostAgendaFeishuDocRef dto : item.getFeishuDocs()) {
                FeishuDocRefs.addFromHostRef(refs, dto);
            }
        } else {
            FeishuDocRefs.addFromUrl(refs, item.getFeishuDocUrl());
        }
    }

    private static void copyHostAgendaFeishuFields(HostAgendaItem from, HostAgendaItem to) {
        if (from.getFeishuDocs() != null && !from.getFeishuDocs().isEmpty()) {
            to.setFeishuDocs(new ArrayList<>(from.getFeishuDocs()));
            applyFirstUrlField(to);
        } else if (from.getFeishuDocUrl() != null && !from.getFeishuDocUrl().isBlank()) {
            to.setFeishuDocUrl(from.getFeishuDocUrl().trim());
        }
    }

    private static HostAgendaItem itemAtIndex(String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank() || agendaIndex < 0) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray() || agendaIndex >= items.size()) {
                return null;
            }
            JsonNode n = items.get(agendaIndex);
            String title = n.path("title").asText("").trim();
            if (title.isEmpty()) {
                return null;
            }
            HostAgendaItem dto = new HostAgendaItem();
            dto.setTitle(title);
            dto.setMinutes(n.path("minutes").asInt(10));
            String detail = n.path("detail").asText("").trim();
            if (!detail.isEmpty()) {
                dto.setDetail(detail);
            }
            JsonNode docs = n.path("feishuDocs");
            if (docs.isArray() && !docs.isEmpty()) {
                List<HostAgendaFeishuDocRef> refList = new ArrayList<>();
                for (JsonNode d : docs) {
                    HostAgendaFeishuDocRef ref = parseFeishuDocNode(d);
                    if (ref != null) {
                        refList.add(ref);
                    }
                }
                if (!refList.isEmpty()) {
                    dto.setFeishuDocs(refList);
                    applyFirstUrlField(dto);
                    return dto;
                }
            }
            String url = n.path("feishuDocUrl").asText("").trim();
            if (url.isEmpty()) {
                String legacyId = n.path("feishuDocToken").asText("").trim();
                url = FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                if (url == null) {
                    url = "";
                }
            }
            if (!url.isEmpty()) {
                dto.setFeishuDocUrl(url);
            }
            return dto;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HostAgendaFeishuDocRef parseFeishuDocNode(JsonNode d) {
        if (d == null || d.isNull()) {
            return null;
        }
        String url = d.path("url").asText("").trim();
        if (url.isEmpty()) {
            url = d.path("feishuDocUrl").asText("").trim();
        }
        if (url.isEmpty()) {
            String legacyId = d.path("token").asText("").trim();
            if (legacyId.isEmpty()) {
                legacyId = d.path("feishuDocToken").asText("").trim();
            }
            url = FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
            if (url == null) {
                url = "";
            }
        }
        if (url.isEmpty()) {
            return null;
        }
        String kind = d.path("kind").asText("").trim();
        if (kind.isEmpty()) {
            FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
            kind = ref != null ? ref.kind().name() : FeishuResourceKind.UNKNOWN.name();
        }
        return HostAgendaFeishuDocRef.builder().kind(kind).url(url).build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
