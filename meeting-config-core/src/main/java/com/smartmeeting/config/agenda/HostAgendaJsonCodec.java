package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.config.feishu.FeishuResourceResolver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * host_agenda JSON 编解码（v2：items[].docs[] 为资料权威存储）。
 */
public final class HostAgendaJsonCodec {

    public static final int VERSION = 2;
    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private HostAgendaJsonCodec() {
    }

    public static List<HostAgendaItem> parseItems(ObjectMapper mapper, String hostAgendaJson) {
        List<HostAgendaItem> out = new ArrayList<>();
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return out;
        }
        try {
            JsonNode root = mapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return out;
            }
            for (JsonNode n : items) {
                HostAgendaItem item = parseItemNode(n);
                if (item != null) {
                    out.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static HostAgendaItem parseItemAtIndex(ObjectMapper mapper, String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank() || agendaIndex < 0) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray() || agendaIndex >= items.size()) {
                return null;
            }
            return parseItemNode(items.get(agendaIndex));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String toJson(ObjectMapper mapper, List<HostAgendaItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("version", VERSION);
            ArrayNode arr = root.putArray("items");
            for (HostAgendaItem item : items) {
                if (item == null || item.getTitle() == null || item.getTitle().isBlank()) {
                    continue;
                }
                ObjectNode n = arr.addObject();
                n.put("title", item.getTitle().trim());
                int min = item.getMinutes() != null && item.getMinutes() > 0 ? item.getMinutes() : 10;
                n.put("minutes", min);
                if (item.getOwners() != null && !item.getOwners().isEmpty()) {
                    ArrayNode ownerArr = n.putArray("owners");
                    for (String owner : item.getOwners()) {
                        if (owner != null && !owner.isBlank()) {
                            ownerArr.add(owner.trim());
                        }
                    }
                }
                if (item.getDetail() != null && !item.getDetail().isBlank()) {
                    n.put("detail", item.getDetail().trim());
                }
                if (item.getOabpTaskSql() != null && !item.getOabpTaskSql().isBlank()) {
                    n.put("oabpTaskSql", item.getOabpTaskSql().strip());
                    n.put("oabpTaskShow", item.getOabpTaskShow() == null || item.getOabpTaskShow());
                }
                List<HostAgendaDocBinding> docs = normalizedDocs(item);
                if (!docs.isEmpty()) {
                    ArrayNode docArr = n.putArray("docs");
                    for (HostAgendaDocBinding doc : docs) {
                        writeDocNode(docArr.addObject(), doc);
                    }
                }
            }
            if (arr.isEmpty()) {
                return null;
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<AgendaDocBindingSnapshot> extractAllBindings(int presetTypeCode, String hostAgendaJson,
                                                                    ObjectMapper mapper) {
        List<AgendaDocBindingSnapshot> out = new ArrayList<>();
        List<HostAgendaItem> items = parseItems(mapper, hostAgendaJson);
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            if (item.getDocs() == null) {
                continue;
            }
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc == null || !doc.isEnabled()) {
                    continue;
                }
                out.add(toSnapshot(doc, presetTypeCode, i));
            }
        }
        return out;
    }

    public static List<AgendaDocBindingSnapshot> extractBindingsForAgenda(int presetTypeCode, String hostAgendaJson,
                                                                          int agendaIndex, ObjectMapper mapper) {
        HostAgendaItem item = parseItemAtIndex(mapper, hostAgendaJson, agendaIndex);
        if (item == null || item.getDocs() == null) {
            return List.of();
        }
        List<AgendaDocBindingSnapshot> out = new ArrayList<>();
        for (HostAgendaDocBinding doc : item.getDocs()) {
            if (doc != null && doc.isEnabled()) {
                out.add(toSnapshot(doc, presetTypeCode, agendaIndex));
            }
        }
        out.sort(Comparator
                .comparingInt((AgendaDocBindingSnapshot b) -> b.getResourceSlot() != null ? b.getResourceSlot() : 0)
                .thenComparing(b -> b.getConfigName() != null ? b.getConfigName() : ""));
        return out;
    }

    public static Optional<AgendaReportBinding> findReportBinding(String hostAgendaJson, int agendaIndex,
                                                                  ObjectMapper mapper) {
        HostAgendaItem item = parseItemAtIndex(mapper, hostAgendaJson, agendaIndex);
        if (item == null || item.getDocs() == null) {
            return Optional.empty();
        }
        HostAgendaDocBinding row = item.getDocs().stream()
                .filter(d -> d != null && d.isEnabled())
                .filter(d -> {
                    String role = d.getRole() != null ? d.getRole().trim().toUpperCase(Locale.ROOT) : "";
                    return "OUTPUT".equals(role) || "BOTH".equals(role);
                })
                .max(Comparator.comparingInt(HostAgendaDocBinding::resolvedSlot))
                .orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        String outputFeishu = null;
        if ("OUTPUT".equalsIgnoreCase(nullToEmpty(row.getRole()))
                && row.getUrl() != null && !row.getUrl().isBlank()) {
            outputFeishu = row.getUrl().trim();
        }
        return Optional.of(new AgendaReportBinding(
                row.getGeneratedReportUrl(),
                row.getGeneratedReportAt(),
                row.getGeneratedReportRunId(),
                outputFeishu));
    }

    public static Optional<LocatedDoc> findDocByConfigName(String hostAgendaJson, String configName,
                                                             ObjectMapper mapper) {
        return findDocContextByConfigName(hostAgendaJson, configName, mapper)
                .map(ctx -> new LocatedDoc(ctx.agendaIndex(), ctx.doc()));
    }

    /**
     * 按 configName 定位 docs[] 条目，并携带父会序项 {@code oabpTaskSql}（weekly-comparison SOURCE 用）。
     */
    public static Optional<DocContext> findDocContextByConfigName(String hostAgendaJson, String configName,
                                                                  ObjectMapper mapper) {
        if (configName == null || configName.isBlank()) {
            return Optional.empty();
        }
        String name = configName.trim();
        List<HostAgendaItem> items = parseItems(mapper, hostAgendaJson);
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            if (item.getDocs() == null) {
                continue;
            }
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc != null && name.equals(doc.getConfigName())) {
                    String sql = item.getOabpTaskSql() != null ? item.getOabpTaskSql().strip() : null;
                    return Optional.of(new DocContext(i, doc, sql));
                }
            }
        }
        return Optional.empty();
    }

    public static String updateGeneratedReport(ObjectMapper mapper, String hostAgendaJson, String configName,
                                               String reportUrl, LocalDateTime generatedAt) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank() || configName == null || configName.isBlank()) {
            return hostAgendaJson;
        }
        String name = configName.trim();
        Optional<LocatedDoc> located = findDocByConfigName(hostAgendaJson, name, mapper);
        if (located.isEmpty()) {
            return hostAgendaJson;
        }
        List<HostAgendaItem> items = parseItems(mapper, hostAgendaJson);
        LocatedDoc loc = located.get();
        if (loc.agendaIndex() >= items.size()) {
            return hostAgendaJson;
        }
        HostAgendaItem item = items.get(loc.agendaIndex());
        if (item.getDocs() == null) {
            return hostAgendaJson;
        }
        for (int j = 0; j < item.getDocs().size(); j++) {
            HostAgendaDocBinding d = item.getDocs().get(j);
            if (d != null && name.equals(d.getConfigName())) {
                d.setGeneratedReportUrl(reportUrl);
                d.setGeneratedReportAt(generatedAt);
                break;
            }
        }
        return toJson(mapper, items);
    }

    /**
     * v0.26：写回最新 run id 与生成时间（不再写 URL）。保留旧 generatedReportUrl 只读兼容。
     */
    public static String updateGeneratedReportRun(ObjectMapper mapper, String hostAgendaJson, String configName,
                                                   Long runId, LocalDateTime generatedAt) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank() || configName == null || configName.isBlank()) {
            return hostAgendaJson;
        }
        String name = configName.trim();
        Optional<LocatedDoc> located = findDocByConfigName(hostAgendaJson, name, mapper);
        if (located.isEmpty()) {
            return hostAgendaJson;
        }
        List<HostAgendaItem> items = parseItems(mapper, hostAgendaJson);
        LocatedDoc loc = located.get();
        if (loc.agendaIndex() >= items.size()) {
            return hostAgendaJson;
        }
        HostAgendaItem item = items.get(loc.agendaIndex());
        if (item.getDocs() == null) {
            return hostAgendaJson;
        }
        for (int j = 0; j < item.getDocs().size(); j++) {
            HostAgendaDocBinding d = item.getDocs().get(j);
            if (d != null && name.equals(d.getConfigName())) {
                d.setGeneratedReportRunId(runId);
                d.setGeneratedReportAt(generatedAt);
                break;
            }
        }
        return toJson(mapper, items);
    }

    public static AgendaDocBindingSnapshot toSnapshot(HostAgendaDocBinding doc, int presetTypeCode, int agendaIndex) {
        return AgendaDocBindingSnapshot.builder()
                .configName(doc.getConfigName())
                .presetTypeCode(presetTypeCode)
                .agendaIndex(agendaIndex)
                .resourceSlot(doc.resolvedSlot())
                .feishuDocUrl(doc.getUrl())
                .storageKind(doc.isLocalStorage() ? AgendaStorageKind.LOCAL : AgendaStorageKind.FEISHU)
                .fileId(doc.getFileId())
                .originalFilename(doc.getOriginalFilename())
                .mimeType(doc.getMimeType())
                .enabled(doc.isEnabled() ? 1 : 0)
                .showInHost(doc.isShowInHost() ? 1 : 0)
                .configRole(doc.getRole() != null ? doc.getRole() : "SOURCE")
                .bitableDisplayMode(doc.getBitableDisplayMode())
                .generatedReportUrl(doc.getGeneratedReportUrl())
                .generatedReportAt(doc.getGeneratedReportAt())
                .generatedReportRunId(doc.getGeneratedReportRunId())
                .build();
    }

    public static HostAgendaDocBinding fromSnapshot(AgendaDocBindingSnapshot snap) {
        if (snap == null) {
            return null;
        }
        String storageKind = AgendaStorageKind.normalize(snap.getStorageKind());
        boolean local = AgendaStorageKind.LOCAL.equals(storageKind);
        String fileId = snap.getFileId() != null ? snap.getFileId().trim() : "";
        if (local && fileId.isEmpty()) {
            return null;
        }
        if (!local && (snap.getConfigName() == null || snap.getConfigName().isBlank())
                && (snap.getFeishuDocUrl() == null || snap.getFeishuDocUrl().isBlank())) {
            return null;
        }
        return HostAgendaDocBinding.builder()
                .configName(snap.getConfigName())
                .role(snap.getConfigRole())
                .slot(snap.getResourceSlot())
                .storageKind(local ? AgendaStorageKind.LOCAL : AgendaStorageKind.FEISHU)
                .url(snap.getFeishuDocUrl())
                .fileId(local ? fileId : null)
                .originalFilename(snap.getOriginalFilename())
                .mimeType(snap.getMimeType())
                .bitableDisplayMode(snap.getBitableDisplayMode())
                .enabled(snap.getEnabled() == null || snap.getEnabled() == 1)
                .showInHost(snap.isShowInHost())
                .generatedReportUrl(snap.getGeneratedReportUrl())
                .generatedReportAt(snap.getGeneratedReportAt())
                .generatedReportRunId(snap.getGeneratedReportRunId())
                .build();
    }

    public static boolean isVersion2(String hostAgendaJson, ObjectMapper mapper) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return false;
        }
        try {
            return mapper.readTree(hostAgendaJson).path("version").asInt(0) == VERSION;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 将独立表 doc 行合并进 host_agenda v2（幂等：已是 v2 则原样返回）。
     */
    public static String mergeDocTableRows(ObjectMapper mapper, String hostAgendaJson, int presetTypeCode,
                                           List<AgendaDocBindingSnapshot> docRows) {
        if (isVersion2(hostAgendaJson, mapper)) {
            return hostAgendaJson;
        }
        List<HostAgendaItem> items = parseItems(mapper, hostAgendaJson);
        if (docRows == null || docRows.isEmpty()) {
            return items.isEmpty() ? hostAgendaJson : toJson(mapper, items);
        }
        for (AgendaDocBindingSnapshot snap : docRows) {
            if (snap == null || snap.getAgendaIndex() == null) {
                continue;
            }
            if (snap.getPresetTypeCode() != null && snap.getPresetTypeCode() != presetTypeCode) {
                continue;
            }
            int idx = snap.getAgendaIndex();
            if (idx < 0 || idx >= items.size()) {
                continue;
            }
            HostAgendaItem item = items.get(idx);
            List<HostAgendaDocBinding> docs = item.getDocs() != null
                    ? new ArrayList<>(item.getDocs()) : new ArrayList<>();
            String name = snap.getConfigName();
            boolean exists = name != null && docs.stream()
                    .anyMatch(d -> d != null && name.equals(d.getConfigName()));
            if (!exists) {
                HostAgendaDocBinding doc = fromSnapshot(snap);
                if (doc != null) {
                    docs.add(doc);
                }
            }
            item.setDocs(docs);
            syncLegacyFeishuFields(item);
        }
        return toJson(mapper, items);
    }

    public record LocatedDoc(int agendaIndex, HostAgendaDocBinding doc) {
    }

    /** docs[] 定位结果 + 父会序 {@code oabpTaskSql}。 */
    public record DocContext(int agendaIndex, HostAgendaDocBinding doc, String oabpTaskSql) {
    }

    /** 从 host_agenda JSON 收集所有 LOCAL 资料的 fileId（用于下载鉴权）。 */
    public static List<String> collectLocalFileIds(ObjectMapper mapper, String hostAgendaJson) {
        List<String> ids = new ArrayList<>();
        for (HostAgendaItem item : parseItems(mapper, hostAgendaJson)) {
            if (item.getDocs() == null) {
                continue;
            }
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc != null && doc.isLocalStorage() && doc.getFileId() != null && !doc.getFileId().isBlank()) {
                    ids.add(doc.getFileId().trim());
                }
            }
        }
        return ids;
    }

    private static HostAgendaItem parseItemNode(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String title = n.path("title").asText("").trim();
        if (title.isEmpty()) {
            return null;
        }
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle(title);
        item.setMinutes(n.path("minutes").asInt(10));
        item.setOwners(parseOwners(n));
        String detail = n.path("detail").asText("").trim();
        if (!detail.isEmpty()) {
            item.setDetail(detail);
        }
        String oabpSql = n.path("oabpTaskSql").asText("").trim();
        if (!oabpSql.isEmpty()) {
            item.setOabpTaskSql(oabpSql);
        }
        if (n.has("oabpTaskShow")) {
            item.setOabpTaskShow(n.path("oabpTaskShow").asBoolean(true));
        } else {
            item.setOabpTaskShow(true);
        }
        List<HostAgendaDocBinding> docs = new ArrayList<>();
        JsonNode docsNode = n.path("docs");
        if (docsNode.isArray()) {
            for (JsonNode d : docsNode) {
                HostAgendaDocBinding doc = parseDocNode(d);
                if (doc != null) {
                    docs.add(doc);
                }
            }
        }
        if (docs.isEmpty()) {
            docs.addAll(promoteLegacyFeishuFields(n));
        }
        if (!docs.isEmpty()) {
            item.setDocs(docs);
            syncLegacyFeishuFields(item);
        }
        return item;
    }

    private static List<HostAgendaDocBinding> promoteLegacyFeishuFields(JsonNode n) {
        List<HostAgendaDocBinding> docs = new ArrayList<>();
        JsonNode feishuDocs = n.path("feishuDocs");
        if (feishuDocs.isArray()) {
            int slot = 0;
            for (JsonNode d : feishuDocs) {
                String url = extractUrlFromNode(d);
                if (!url.isBlank()) {
                    docs.add(HostAgendaDocBinding.builder()
                            .role("SOURCE")
                            .slot(slot++)
                            .url(url)
                            .enabled(true)
                            .build());
                }
            }
            return docs;
        }
        String url = n.path("feishuDocUrl").asText("").trim();
        if (url.isEmpty()) {
            url = FeishuResourceResolver.legacyDocIdToDocxUrl(n.path("feishuDocToken").asText("").trim());
            if (url == null) {
                url = "";
            }
        }
        if (!url.isBlank()) {
            docs.add(HostAgendaDocBinding.builder()
                    .role("SOURCE")
                    .slot(0)
                    .url(url)
                    .enabled(true)
                    .build());
        }
        return docs;
    }

    private static HostAgendaDocBinding parseDocNode(JsonNode d) {
        if (d == null || d.isNull()) {
            return null;
        }
        String url = extractUrlFromNode(d);
        String configName = d.path("configName").asText("").trim();
        if (configName.isEmpty()) {
            configName = d.path("config_name").asText("").trim();
        }
        String role = d.path("role").asText("").trim();
        if (role.isEmpty()) {
            role = d.path("configRole").asText("").trim();
        }
        if (role.isEmpty()) {
            role = d.path("config_role").asText("SOURCE").trim();
        }
        String storageKind = AgendaStorageKind.normalize(d.path("storageKind").asText("").trim());
        String fileId = d.path("fileId").asText("").trim();
        if (fileId.isEmpty()) {
            fileId = d.path("file_id").asText("").trim();
        }
        String originalFilename = d.path("originalFilename").asText("").trim();
        if (originalFilename.isEmpty()) {
            originalFilename = d.path("original_filename").asText("").trim();
        }
        String mimeType = d.path("mimeType").asText("").trim();
        if (mimeType.isEmpty()) {
            mimeType = d.path("mime_type").asText("").trim();
        }
        boolean local = AgendaStorageKind.LOCAL.equals(storageKind) && !fileId.isEmpty();
        if (!local && configName.isEmpty() && url.isBlank()) {
            return null;
        }
        if (local && configName.isEmpty() && originalFilename.isEmpty()) {
            return null;
        }
        String bdm = d.path("bitableDisplayMode").asText("").trim();
        if (bdm.isEmpty()) {
            bdm = d.path("bitable_display_mode").asText("").trim();
        }
        LocalDateTime genAt = null;
        String genAtStr = d.path("generatedReportAt").asText("").trim();
        if (genAtStr.isEmpty()) {
            genAtStr = d.path("generated_report_at").asText("").trim();
        }
        if (!genAtStr.isEmpty()) {
            try {
                genAt = LocalDateTime.parse(genAtStr, ISO_LOCAL);
            } catch (Exception ignored) {
            }
        }
        String genUrl = d.path("generatedReportUrl").asText("").trim();
        if (genUrl.isEmpty()) {
            genUrl = d.path("generated_report_url").asText("").trim();
        }
        Long genRunId = null;
        if (d.has("generatedReportRunId") && !d.path("generatedReportRunId").isNull()) {
            genRunId = d.path("generatedReportRunId").asLong(0);
            if (genRunId == 0) {
                genRunId = null;
            }
        } else if (d.has("generated_report_run_id") && !d.path("generated_report_run_id").isNull()) {
            genRunId = d.path("generated_report_run_id").asLong(0);
            if (genRunId == 0) {
                genRunId = null;
            }
        }
        return HostAgendaDocBinding.builder()
                .configName(configName.isEmpty() ? null : configName)
                .role(role.isEmpty() ? "SOURCE" : role)
                .slot(d.path("slot").asInt(d.path("resourceSlot").asInt(d.path("resource_slot").asInt(0))))
                .storageKind(local ? AgendaStorageKind.LOCAL : AgendaStorageKind.FEISHU)
                .url(url.isBlank() ? null : url)
                .fileId(local ? fileId : null)
                .originalFilename(originalFilename.isEmpty() ? null : originalFilename)
                .mimeType(mimeType.isEmpty() ? null : mimeType)
                .bitableDisplayMode(bdm.isEmpty() ? null : bdm)
                .enabled(!d.has("enabled") || d.path("enabled").asInt(1) == 1)
                .showInHost(!d.has("showInHost") || d.path("showInHost").asBoolean(true))
                .generatedReportUrl(genUrl.isEmpty() ? null : genUrl)
                .generatedReportAt(genAt)
                .generatedReportRunId(genRunId)
                .build();
    }

    private static String extractUrlFromNode(JsonNode d) {
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
        return url;
    }

    private static void writeDocNode(ObjectNode n, HostAgendaDocBinding doc) {
        if (doc.getConfigName() != null && !doc.getConfigName().isBlank()) {
            n.put("configName", doc.getConfigName().trim());
        }
        n.put("role", doc.getRole() != null ? doc.getRole() : "SOURCE");
        n.put("slot", doc.resolvedSlot());
        if (doc.isLocalStorage()) {
            n.put("storageKind", AgendaStorageKind.LOCAL);
            if (doc.getFileId() != null && !doc.getFileId().isBlank()) {
                n.put("fileId", doc.getFileId().trim());
            }
            if (doc.getOriginalFilename() != null && !doc.getOriginalFilename().isBlank()) {
                n.put("originalFilename", doc.getOriginalFilename().trim());
            }
            if (doc.getMimeType() != null && !doc.getMimeType().isBlank()) {
                n.put("mimeType", doc.getMimeType().trim());
            }
        } else if (doc.getUrl() != null && !doc.getUrl().isBlank()) {
            n.put("url", doc.getUrl().trim());
        }
        if (doc.getBitableDisplayMode() != null && !doc.getBitableDisplayMode().isBlank()) {
            n.put("bitableDisplayMode", doc.getBitableDisplayMode());
        }
        n.put("enabled", doc.isEnabled());
        n.put("showInHost", doc.isShowInHost());
        if (doc.getGeneratedReportUrl() != null && !doc.getGeneratedReportUrl().isBlank()) {
            n.put("generatedReportUrl", doc.getGeneratedReportUrl().trim());
        }
        if (doc.getGeneratedReportRunId() != null) {
            n.put("generatedReportRunId", doc.getGeneratedReportRunId());
        }
        if (doc.getGeneratedReportAt() != null) {
            n.put("generatedReportAt", doc.getGeneratedReportAt().format(ISO_LOCAL));
        }
    }

    private static List<HostAgendaDocBinding> normalizedDocs(HostAgendaItem item) {
        if (item.getDocs() != null && !item.getDocs().isEmpty()) {
            return item.getDocs();
        }
        return promoteLegacyFeishuFieldsFromItem(item);
    }

    private static List<HostAgendaDocBinding> promoteLegacyFeishuFieldsFromItem(HostAgendaItem item) {
        List<HostAgendaDocBinding> docs = new ArrayList<>();
        if (item.getFeishuDocs() != null) {
            int slot = 0;
            for (var ref : item.getFeishuDocs()) {
                if (ref != null && ref.getUrl() != null && !ref.getUrl().isBlank()) {
                    docs.add(HostAgendaDocBinding.builder()
                            .role("SOURCE")
                            .slot(slot++)
                            .url(ref.getUrl().trim())
                            .enabled(true)
                            .build());
                }
            }
        } else if (item.getFeishuDocUrl() != null && !item.getFeishuDocUrl().isBlank()) {
            docs.add(HostAgendaDocBinding.builder()
                    .role("SOURCE")
                    .slot(0)
                    .url(item.getFeishuDocUrl().trim())
                    .enabled(true)
                    .build());
        }
        return docs;
    }

    private static void syncLegacyFeishuFields(HostAgendaItem item) {
        if (item.getDocs() == null || item.getDocs().isEmpty()) {
            return;
        }
        List<HostAgendaFeishuDocRef> refs = new ArrayList<>();
        for (HostAgendaDocBinding doc : item.getDocs()) {
            if (doc.getUrl() == null || doc.getUrl().isBlank()) {
                continue;
            }
            if (!AgendaDocRoleRules.isSourceRoleForMerge(toSnapshot(doc, 0, 0))) {
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

    private static List<String> parseOwners(JsonNode n) {
        List<String> owners = new ArrayList<>();
        JsonNode ownerNode = n.path("owners");
        if (ownerNode.isArray()) {
            for (JsonNode x : ownerNode) {
                String uid = x.asText("").trim();
                if (!uid.isEmpty() && !owners.contains(uid)) {
                    owners.add(uid);
                }
            }
            return owners;
        }
        String single = ownerNode.asText("").trim();
        if (!single.isEmpty()) {
            for (String part : single.split(",")) {
                String uid = part.trim();
                if (!uid.isEmpty() && !owners.contains(uid)) {
                    owners.add(uid);
                }
            }
        }
        return owners;
    }
}
