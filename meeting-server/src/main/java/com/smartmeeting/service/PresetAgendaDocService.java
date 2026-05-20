package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.AgendaDocContentResponse;
import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.service.feishu.FeishuDocRefs;
import com.smartmeeting.service.feishu.FeishuResourceKind;
import com.smartmeeting.service.feishu.FeishuResourceRef;
import com.smartmeeting.service.feishu.FeishuResourceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预设会序飞书资料绑定服务：按 preset 会序关联 docx / wiki / base 资料，支持同一会序多条（resource_slot）。
 * <p>
 * 主要协作组件：{@link MatterProgressDocConfigMapper}、{@link FeishuService}（拉取正文）、
 * {@link FeishuResourceResolver}、{@link FeishuDocRefs}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresetAgendaDocService {

    private final MatterProgressDocConfigMapper configMapper;
    private final FeishuService feishuService;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定预设类型下所有已启用的会序资料配置。
     *
     * @param presetTypeCode 预设类型编码（1–5）
     * @return 配置列表；编码无效时返回空列表
     */
    public List<MatterProgressDocConfig> listEnabledByPreset(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return List.of();
        }
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .isNotNull(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        return configMapper.selectList(q);
    }

    /**
     * 查询指定预设类型与会序索引下的资料配置。
     *
     * @param presetTypeCode 预设类型编码（1–5）
     * @param agendaIndex    会序索引（从 0 开始）
     * @return 配置列表；参数无效时返回空列表
     */
    public List<MatterProgressDocConfig> listConfigsForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0) {
            return List.of();
        }
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .eq(MatterProgressDocConfig::getAgendaIndex, agendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        return configMapper.selectList(q);
    }

    /**
     * 为主持会序 DTO 列表 enrichment 飞书资料引用（仅填充尚未配置资料的项）。
     *
     * @param presetTypeCode 预设类型编码
     * @param items          主持会序 DTO 列表（原地修改）
     */
    public void enrichHostAgendaItems(int presetTypeCode, List<HostAgendaItemDto> items) {
        if (items == null || items.isEmpty() || presetTypeCode < 1 || presetTypeCode > 5) {
            return;
        }
        Map<Integer, List<MatterProgressDocConfig>> byAgenda = groupConfigsByAgenda(listEnabledByPreset(presetTypeCode));
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItemDto item = items.get(i);
            if (item == null) {
                continue;
            }
            if (hasDocRef(item) || (item.getFeishuDocs() != null && !item.getFeishuDocs().isEmpty())) {
                continue;
            }
            List<MatterProgressDocConfig> cfgs = byAgenda.get(i);
            if (cfgs == null || cfgs.isEmpty()) {
                continue;
            }
            item.setFeishuDocs(configsToDtoList(cfgs));
            applyFirstUrlField(item);
        }
    }

    /**
     * 列出指定会序的全部飞书资料引用 DTO。
     *
     * @param meeting     会议实体
     * @param agendaIndex 会序索引
     * @return 资料引用 DTO 列表
     */
    public List<FeishuDocRefDto> listDocRefsForAgenda(Meeting meeting, int agendaIndex) {
        List<FeishuResourceRef> refs = resolveAllResources(meeting, agendaIndex, null);
        return refs.stream().map(FeishuDocRefs::toDto).toList();
    }

    /**
     * 解析会序的首个飞书资源引用（合并运行时 URL、host_agenda JSON 与 preset 配置）。
     *
     * @param meeting        会议实体
     * @param agendaIndex    会序索引
     * @param runtimeDocUrl  运行时传入的文档 URL（可为 null）
     * @return 首个资源引用；无匹配时返回 {@code null}
     */
    public FeishuResourceRef resolveResource(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        List<FeishuDocRefDto> runtime = null;
        if (runtimeDocUrl != null && !runtimeDocUrl.isBlank()) {
            runtime = List.of(FeishuDocRefDto.builder().url(runtimeDocUrl.trim()).build());
        }
        List<FeishuResourceRef> all = resolveAllResources(meeting, agendaIndex, runtime);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 解析会序的全部飞书资源引用并去重合并。
     *
     * @param meeting     会议实体
     * @param agendaIndex 会序索引
     * @param runtimeDocs 运行时传入的资料引用列表（可为 null）
     * @return 去重后的资源引用列表
     */
    public List<FeishuResourceRef> resolveAllResources(Meeting meeting, int agendaIndex,
                                                       List<FeishuDocRefDto> runtimeDocs) {
        List<FeishuResourceRef> refs = new ArrayList<>();
        if (runtimeDocs != null) {
            for (FeishuDocRefDto dto : runtimeDocs) {
                FeishuDocRefs.addFromDto(refs, dto);
            }
        }
        if (meeting != null && meeting.getHostAgenda() != null) {
            HostAgendaItemDto fromJson = itemAtIndex(meeting.getHostAgenda(), agendaIndex);
            if (fromJson != null) {
                if (fromJson.getFeishuDocs() != null) {
                    for (FeishuDocRefDto dto : fromJson.getFeishuDocs()) {
                        FeishuDocRefs.addFromDto(refs, dto);
                    }
                } else {
                    FeishuDocRefs.addFromUrl(refs, fromJson.getFeishuDocUrl());
                }
            }
        }
        Integer preset = meeting != null ? meeting.getPresetTypeCode() : null;
        if (preset != null && preset >= 1 && preset <= 5) {
            for (MatterProgressDocConfig cfg : listConfigsForAgenda(preset, agendaIndex)) {
                FeishuDocRefs.addFromConfig(refs, cfg);
            }
        }
        return FeishuDocRefs.mergeDistinct(refs);
    }

    /**
     * 拉取会序关联的全部飞书资料正文并组装为 {@link AgendaDocContentResponse}。
     *
     * @param meeting     会议实体
     * @param agendaIndex 会序索引
     * @param agendaTitle 会序标题（用于错误提示）
     * @param runtimeDocs 运行时传入的资料引用（可为 null）
     * @return 会序资料内容响应（含合并正文与各 part 详情）
     * @throws BusinessException 未配置资料（404）或全部拉取失败且无外链（502）
     */
    public AgendaDocContentResponse buildAgendaDocContent(Meeting meeting, int agendaIndex, String agendaTitle,
                                                          List<FeishuDocRefDto> runtimeDocs) {
        List<FeishuResourceRef> refs = resolveAllResources(meeting, agendaIndex, runtimeDocs);
        if (refs.isEmpty()) {
            throw new BusinessException(404,
                    "会序 " + (agendaIndex + 1) + (agendaTitle != null && !agendaTitle.isBlank()
                            ? "「" + agendaTitle + "」" : "")
                            + " 未配置飞书资料");
        }
        List<AgendaDocPartDto> parts = new ArrayList<>();
        StringBuilder combined = new StringBuilder();
        for (FeishuResourceRef ref : refs) {
            String openUrl = ref.defaultOpenUrl();
            AgendaDocPartDto.AgendaDocPartDtoBuilder partBuilder = AgendaDocPartDto.builder()
                    .docKind(ref.kind().name())
                    .feishuDocUrl(openUrl);
            if (ref.canFetchPlainText()) {
                try {
                    String text = feishuService.fetchResourcePlainText(ref);
                    if (text != null && !text.isBlank()) {
                        partBuilder.plainText(text);
                        combined.append('【').append(FeishuDocRefs.kindLabel(ref.kind())).append("】\n")
                                .append(text.trim()).append("\n\n");
                    } else {
                        partBuilder.fetchError("资料已读取但正文为空");
                    }
                } catch (BusinessException e) {
                    partBuilder.fetchError(e.getMessage());
                } catch (Exception e) {
                    log.warn("fetch part agendaIndex={} kind={}: {}", agendaIndex, ref.kind(), e.getMessage());
                    partBuilder.fetchError("拉取失败: " + e.getMessage());
                }
            } else {
                partBuilder.fetchError("无法内嵌拉取正文（base 须在 URL 带 table=），请使用下方链接在飞书中打开");
            }
            parts.add(partBuilder.build());
        }
        boolean anyText = parts.stream().anyMatch(p -> p.getPlainText() != null && !p.getPlainText().isBlank());
        if (!anyText && parts.stream().allMatch(p -> p.getFetchError() != null)) {
            boolean anyUrl = parts.stream().anyMatch(p -> p.getFeishuDocUrl() != null && !p.getFeishuDocUrl().isBlank());
            if (!anyUrl) {
                throw new BusinessException(502,
                        "会序 " + (agendaIndex + 1) + " 全部飞书资料拉取失败，请检查权限或链接");
            }
            log.warn("会序 {} 飞书正文均未拉取成功，返回外链与错误说明供主持页展示 agendaIndex={}", agendaIndex + 1,
                    agendaIndex);
        }
        FeishuResourceRef first = refs.get(0);
        return AgendaDocContentResponse.builder()
                .agendaIndex(agendaIndex)
                .agendaTitle(agendaTitle)
                .documentId(first.primaryToken())
                .feishuDocUrl(first.defaultOpenUrl())
                .docKind(first.kind().name())
                .plainText(combined.toString().trim())
                .parts(parts)
                .build();
    }

    /**
     * 解析会序首个飞书资料的外链 URL。
     *
     * @param meeting        会议实体
     * @param agendaIndex    会序索引
     * @param runtimeDocUrl  运行时 URL（可为 null）
     * @return 外链 URL；无匹配时返回 {@code null}
     */
    public String resolveFeishuDocUrl(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        FeishuResourceRef ref = resolveResource(meeting, agendaIndex, runtimeDocUrl);
        return ref != null ? ref.defaultOpenUrl() : null;
    }

    /**
     * 解析会序首个飞书资料的主 token（document_id / node_token / app_token）。
     *
     * @param meeting        会议实体
     * @param agendaIndex    会序索引
     * @param runtimeDocUrl  运行时 URL（可为 null）
     * @return 主 token；无匹配时返回 {@code null}
     */
    public String resolveDocumentId(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        FeishuResourceRef ref = resolveResource(meeting, agendaIndex, runtimeDocUrl);
        return ref != null ? ref.primaryToken() : null;
    }

    /** 按会序索引分组资料配置并按 resource_slot、id 排序。 */
    private static Map<Integer, List<MatterProgressDocConfig>> groupConfigsByAgenda(
            List<MatterProgressDocConfig> configs) {
        Map<Integer, List<MatterProgressDocConfig>> map = new HashMap<>();
        if (configs == null) {
            return map;
        }
        for (MatterProgressDocConfig c : configs) {
            if (c.getAgendaIndex() == null || c.getAgendaIndex() < 0) {
                continue;
            }
            map.computeIfAbsent(c.getAgendaIndex(), k -> new ArrayList<>()).add(c);
        }
        for (List<MatterProgressDocConfig> list : map.values()) {
            list.sort(Comparator
                    .comparingInt((MatterProgressDocConfig c) -> c.getResourceSlot() != null ? c.getResourceSlot() : 0)
                    .thenComparingLong(c -> c.getId() != null ? c.getId() : 0L));
        }
        return map;
    }

    /** 将资料配置列表转为 {@link FeishuDocRefDto} 列表。 */
    private static List<FeishuDocRefDto> configsToDtoList(List<MatterProgressDocConfig> cfgs) {
        List<FeishuDocRefDto> out = new ArrayList<>();
        for (MatterProgressDocConfig cfg : cfgs) {
            FeishuResourceRef ref = FeishuResourceResolver.resolve(cfg);
            FeishuDocRefDto dto = FeishuDocRefs.toDto(ref);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    private static void applyFirstUrlField(HostAgendaItemDto item) {
        if (item.getFeishuDocs() == null || item.getFeishuDocs().isEmpty()) {
            return;
        }
        FeishuDocRefDto first = item.getFeishuDocs().get(0);
        if (first.getUrl() != null && !first.getUrl().isBlank()) {
            item.setFeishuDocUrl(first.getUrl());
        }
    }

    private static boolean hasDocRef(HostAgendaItemDto item) {
        return FeishuResourceResolver.isRecognizedFeishuDocUrl(item.getFeishuDocUrl());
    }

    /** 从 host_agenda JSON 解析指定索引的会序 DTO，兼容 feishuDocs 数组与旧版 feishuDocToken。 */
    private HostAgendaItemDto itemAtIndex(String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank() || agendaIndex < 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(hostAgendaJson);
            JsonNode items = root.path("items");
            if (!items.isArray() || agendaIndex >= items.size()) {
                return null;
            }
            JsonNode n = items.get(agendaIndex);
            String title = n.path("title").asText("").trim();
            if (title.isEmpty()) {
                return null;
            }
            HostAgendaItemDto dto = new HostAgendaItemDto();
            dto.setTitle(title);
            dto.setMinutes(n.path("minutes").asInt(10));
            String detail = n.path("detail").asText("").trim();
            if (!detail.isEmpty()) {
                dto.setDetail(detail);
            }
            JsonNode docs = n.path("feishuDocs");
            if (docs.isArray() && !docs.isEmpty()) {
                List<FeishuDocRefDto> refList = new ArrayList<>();
                for (JsonNode d : docs) {
                    FeishuDocRefDto ref = parseFeishuDocNode(d);
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
        } catch (Exception e) {
            log.warn("Parse host_agenda item at {}: {}", agendaIndex, e.getMessage());
            return null;
        }
    }

    private static FeishuDocRefDto parseFeishuDocNode(JsonNode d) {
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
        return FeishuDocRefDto.builder().kind(kind).url(url).build();
    }
}
