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
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.service.cache.MeetingPresetCacheService;
import com.smartmeeting.service.cache.PresetBundle;
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
import java.util.Optional;

/**
 * 预设会序飞书资料绑定服务。
 * <p>
 * 飞书资料优先级（主持运行时）：① 本会 {@code int_meeting.host_agenda}（创建时快照）→ ② 预设模板
 * {@code int_meeting_type_preset.host_agenda} → ③ {@code int_matter_progress_doc_config}。
 * 创建会议时经 {@link #syncHostAgendaForCreate} 将 ②+③ 合并写入本会快照。
 * <p>
 * 预设与资料配置经 {@link MeetingPresetCacheService} 走 Redis（不可用时进程内缓存）。
 * <p>
 * 主要协作组件：{@link MatterProgressDocConfigMapper}、{@link FeishuService}（拉取正文）、
 * {@link FeishuResourceResolver}、{@link FeishuDocRefs}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresetAgendaDocService {

    private final MatterProgressDocConfigMapper configMapper;
    private final MeetingTypePresetMapper presetMapper;
    private final MeetingPresetCacheService presetCache;
    private final FeishuService feishuService;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定预设类型下所有已启用的会序资料配置。
     *
     * @param presetTypeCode 预设类型编码（1–5）
     * @return 配置列表；编码无效时返回空列表
     */
    /**
     * 从 DB 强制刷新指定 preset 的 Redis 缓存（创建会议、定时任务、运维刷新）。
     */
    public PresetBundle refreshPresetBundle(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return new PresetBundle(null, List.of());
        }
        MeetingTypePreset preset = presetMapper.selectById(presetTypeCode);
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .isNotNull(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        List<MatterProgressDocConfig> docs = configMapper.selectList(q);
        return presetCache.putPresetBundle(presetTypeCode, preset, docs);
    }

    /** 定时预热：刷新 preset code 1～5。 */
    public void refreshAllPresetBundles() {
        for (int code = 1; code <= 5; code++) {
            try {
                refreshPresetBundle(code);
            } catch (Exception e) {
                log.warn("refreshPresetBundle code={} failed: {}", code, e.getMessage());
            }
        }
    }

    /**
     * 创建会议时：刷新 Redis → 合并飞书绑定 → 序列化为 {@code int_meeting.host_agenda} JSON。
     *
     * @param presetTypeCode 预设类型 1～5
     * @param requestItems   合并后的会序项（可为空，则取自预设模板）
     * @return host_agenda JSON；无有效会序时返回 null
     */
    public String syncHostAgendaForCreate(int presetTypeCode, List<HostAgendaItemDto> requestItems) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return toHostAgendaJson(requestItems);
        }
        refreshPresetBundle(presetTypeCode);
        List<HostAgendaItemDto> items = copyHostAgendaItems(requestItems);
        if (items.isEmpty()) {
            MeetingTypePreset preset = getPresetCached(presetTypeCode);
            if (preset != null && preset.getHostAgenda() != null && !preset.getHostAgenda().isBlank()) {
                items = parseHostAgendaItems(preset.getHostAgenda());
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        enrichHostAgendaItems(presetTypeCode, items);
        return toHostAgendaJson(items);
    }

    /**
     * 将主持会序 DTO 列表序列化为 {@code {"items":[...]}} 形态。
     */
    public String toHostAgendaJson(List<HostAgendaItemDto> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        try {
            var root = objectMapper.createObjectNode();
            var arr = root.putArray("items");
            for (HostAgendaItemDto dto : items) {
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
                    for (FeishuDocRefDto ref : dto.getFeishuDocs()) {
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
            log.warn("Failed to serialize host_agenda: {}", e.getMessage());
            return null;
        }
    }

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
        return presetCache.getMatterProgressDocs(presetTypeCode, () -> configMapper.selectList(q)).stream()
                .filter(PresetAgendaDocService::isSourceRoleForMerge)
                .toList();
    }

    /**
     * 上会只读：OUTPUT/BOTH 行的 generated_report_url（及 OUTPUT 可选 feishu_doc_url）。
     */
    public Optional<AgendaReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0) {
            return Optional.empty();
        }
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .eq(MatterProgressDocConfig::getAgendaIndex, agendaIndex)
                .in(MatterProgressDocConfig::getConfigRole, "OUTPUT", "BOTH")
                .orderByDesc(MatterProgressDocConfig::getId)
                .last("LIMIT 1");
        MatterProgressDocConfig row = configMapper.selectOne(q);
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

    /** 会序通报只读绑定（主持页 WS） */
    public record AgendaReportBinding(
            String generatedReportUrl,
            java.time.LocalDateTime generatedReportAt,
            String outputFeishuDocUrl) {
    }

    static boolean isSourceRoleForMerge(MatterProgressDocConfig cfg) {
        if (cfg == null) {
            return false;
        }
        String role = cfg.getConfigRole();
        if (role == null || role.isBlank()) {
            return true;
        }
        role = role.trim().toUpperCase(java.util.Locale.ROOT);
        return "SOURCE".equals(role) || "BOTH".equals(role);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
        return listConfigsForAgendaFromDb(presetTypeCode, agendaIndex);
    }

    /**
     * 直查库（不经 Redis），用于会序资料配置解析。
     */
    public List<MatterProgressDocConfig> listConfigsForAgendaFromDb(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0) {
            return List.of();
        }
        if (configMapper == null) {
            return listEnabledByPreset(presetTypeCode).stream()
                    .filter(c -> c.getAgendaIndex() != null && c.getAgendaIndex() == agendaIndex)
                    .sorted(Comparator
                            .comparingInt((MatterProgressDocConfig c) ->
                                    c.getResourceSlot() != null ? c.getResourceSlot() : 0)
                            .thenComparingLong(c -> c.getId() != null ? c.getId() : 0L))
                    .toList();
        }
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .eq(MatterProgressDocConfig::getAgendaIndex, agendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        List<MatterProgressDocConfig> rows = configMapper.selectList(q);
        return rows != null ? rows : List.of();
    }

    /**
     * 为会序 DTO 列表补全飞书引用（用于展示）：先读预设模板 host_agenda，再回退配置表；不覆盖项上已有飞书字段（本会记录）。
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
            if (!hostAgendaItemHasFeishu(item)) {
                HostAgendaItemDto fromTemplate = hostAgendaItemAtPresetTemplate(presetTypeCode, i);
                if (hostAgendaItemHasFeishu(fromTemplate)) {
                    copyHostAgendaFeishuFields(fromTemplate, item);
                } else {
                    List<MatterProgressDocConfig> cfgs = byAgenda.get(i);
                    if (cfgs != null && !cfgs.isEmpty()) {
                        item.setFeishuDocs(configsToDtoList(cfgs));
                        applyFirstUrlField(item);
                    }
                }
            }
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
        if (meeting != null && meeting.getHostAgenda() != null && !meeting.getHostAgenda().isBlank()) {
            HostAgendaItemDto fromMeeting = itemAtIndex(meeting.getHostAgenda(), agendaIndex);
            if (hostAgendaItemHasFeishu(fromMeeting)) {
                addHostAgendaItemFeishuRefs(refs, fromMeeting);
                return FeishuDocRefs.mergeDistinct(refs);
            }
        }
        Integer preset = meeting != null ? meeting.getPresetTypeCode() : null;
        if (preset != null && preset >= 1 && preset <= 5) {
            HostAgendaItemDto fromTemplate = hostAgendaItemAtPresetTemplate(preset, agendaIndex);
            if (hostAgendaItemHasFeishu(fromTemplate)) {
                addHostAgendaItemFeishuRefs(refs, fromTemplate);
                return FeishuDocRefs.mergeDistinct(refs);
            }
            for (MatterProgressDocConfig cfg : listConfigsForAgenda(preset, agendaIndex)) {
                if (!isSourceRoleForMerge(cfg)) {
                    continue;
                }
                FeishuDocRefs.addFromConfig(refs, cfg);
            }
        }
        return FeishuDocRefs.mergeDistinct(refs);
    }

    /**
     * 预设模板 host_agenda 是否已为该会序配置飞书资料（为 true 时忽略 int_matter_progress_doc_config）。
     */
    public boolean presetTemplateDefinesFeishuForIndex(int presetTypeCode, int agendaIndex) {
        return hostAgendaItemHasFeishu(hostAgendaItemAtPresetTemplate(presetTypeCode, agendaIndex));
    }

    /**
     * 按 code 加载预设（Redis 缓存，未命中再查库）。
     */
    public MeetingTypePreset getPresetCached(int presetTypeCode) {
        if (presetMapper == null || presetTypeCode < 1 || presetTypeCode > 5) {
            return null;
        }
        return presetCache.getPreset(presetTypeCode, () -> presetMapper.selectById(presetTypeCode));
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
        return hostAgendaItemHasFeishu(item);
    }

    private static List<HostAgendaItemDto> copyHostAgendaItems(List<HostAgendaItemDto> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<HostAgendaItemDto> out = new ArrayList<>();
        for (HostAgendaItemDto src : source) {
            if (src == null || src.getTitle() == null || src.getTitle().isBlank()) {
                continue;
            }
            HostAgendaItemDto dto = new HostAgendaItemDto();
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

    /** 从 host_agenda JSON 解析全部会序项（含飞书字段）。 */
    private List<HostAgendaItemDto> parseHostAgendaItems(String hostAgendaJson) {
        List<HostAgendaItemDto> out = new ArrayList<>();
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
                HostAgendaItemDto dto = itemAtIndex(hostAgendaJson, i);
                if (dto != null) {
                    out.add(dto);
                }
            }
        } catch (Exception e) {
            log.warn("Parse host_agenda items failed: {}", e.getMessage());
        }
        return out;
    }

    private HostAgendaItemDto hostAgendaItemAtPresetTemplate(int presetTypeCode, int agendaIndex) {
        MeetingTypePreset preset = getPresetCached(presetTypeCode);
        if (preset == null || preset.getHostAgenda() == null || preset.getHostAgenda().isBlank()) {
            return null;
        }
        return itemAtIndex(preset.getHostAgenda(), agendaIndex);
    }

    public static boolean hostAgendaItemHasFeishu(HostAgendaItemDto item) {
        if (item == null) {
            return false;
        }
        if (item.getFeishuDocs() != null) {
            for (FeishuDocRefDto d : item.getFeishuDocs()) {
                if (d != null && FeishuResourceResolver.isRecognizedFeishuDocUrl(d.getUrl())) {
                    return true;
                }
            }
        }
        return FeishuResourceResolver.isRecognizedFeishuDocUrl(item.getFeishuDocUrl());
    }

    private static void addHostAgendaItemFeishuRefs(List<FeishuResourceRef> refs, HostAgendaItemDto item) {
        if (item.getFeishuDocs() != null) {
            for (FeishuDocRefDto dto : item.getFeishuDocs()) {
                FeishuDocRefs.addFromDto(refs, dto);
            }
        } else {
            FeishuDocRefs.addFromUrl(refs, item.getFeishuDocUrl());
        }
    }

    private static void copyHostAgendaFeishuFields(HostAgendaItemDto from, HostAgendaItemDto to) {
        if (from.getFeishuDocs() != null && !from.getFeishuDocs().isEmpty()) {
            to.setFeishuDocs(new ArrayList<>(from.getFeishuDocs()));
            applyFirstUrlField(to);
        } else if (from.getFeishuDocUrl() != null && !from.getFeishuDocUrl().isBlank()) {
            to.setFeishuDocUrl(from.getFeishuDocUrl().trim());
        }
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
