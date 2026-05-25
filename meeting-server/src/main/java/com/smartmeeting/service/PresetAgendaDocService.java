package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.AgendaDocContentResponse;
import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.AgendaWeeklyReportDto;
import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.config.agenda.AgendaBindingConverter;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaDocRoleRules;
import com.smartmeeting.config.agenda.AgendaReportBinding;
import com.smartmeeting.config.agenda.HostAgendaDtoConverter;
import com.smartmeeting.config.agenda.HostAgendaFeishuDocRef;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.service.cache.MeetingPresetCacheService;
import com.smartmeeting.service.cache.PresetBundle;
import com.smartmeeting.service.feishu.FeishuDocRefs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 预设会序飞书资料绑定服务（应用层）。
 * <p>
 * 合并 / enrich / 资源解析委托 {@link PresetAgendaMergeEngine}（meeting-config-core）；
 * 本类负责 DB、Redis 缓存与飞书正文 HTTP 拉取。
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

    public PresetBundle refreshPresetBundle(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return new PresetBundle(null, List.of());
        }
        MeetingTypePreset preset = presetMapper.selectById(presetTypeCode);
        LambdaQueryWrapper<MatterProgressDocConfig> q = enabledPresetQuery(presetTypeCode);
        List<MatterProgressDocConfig> docs = configMapper.selectList(q);
        return presetCache.putPresetBundle(presetTypeCode, preset, docs);
    }

    public void refreshAllPresetBundles() {
        for (int code = 1; code <= 5; code++) {
            try {
                refreshPresetBundle(code);
            } catch (Exception e) {
                log.warn("refreshPresetBundle code={} failed: {}", code, e.getMessage());
            }
        }
    }

    public String syncHostAgendaForCreate(int presetTypeCode, List<HostAgendaItemDto> requestItems) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return PresetAgendaMergeEngine.toHostAgendaJson(objectMapper, HostAgendaDtoConverter.toCoreList(requestItems));
        }
        refreshPresetBundle(presetTypeCode);
        String presetJson = presetHostAgendaJson(presetTypeCode);
        return PresetAgendaMergeEngine.syncHostAgendaJson(
                objectMapper,
                presetTypeCode,
                presetJson,
                HostAgendaDtoConverter.toCoreList(requestItems),
                listEnabledSnapshotsByPreset(presetTypeCode));
    }

    public String toHostAgendaJson(List<HostAgendaItemDto> items) {
        return PresetAgendaMergeEngine.toHostAgendaJson(objectMapper, HostAgendaDtoConverter.toCoreList(items));
    }

    public List<MatterProgressDocConfig> listEnabledByPreset(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return List.of();
        }
        LambdaQueryWrapper<MatterProgressDocConfig> q = enabledPresetQuery(presetTypeCode);
        return presetCache.getMatterProgressDocs(presetTypeCode, () -> configMapper.selectList(q)).stream()
                .filter(PresetAgendaDocService::isSourceRoleForMerge)
                .toList();
    }

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
        return PresetAgendaMergeEngine.findReportBinding(List.of(AgendaBindingConverter.from(row)), presetTypeCode, agendaIndex);
    }

    public static boolean isSourceRoleForMerge(MatterProgressDocConfig cfg) {
        return AgendaDocRoleRules.isSourceRoleForMerge(AgendaBindingConverter.from(cfg));
    }

    public List<MatterProgressDocConfig> listConfigsForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0) {
            return List.of();
        }
        return listConfigsForAgendaFromDb(presetTypeCode, agendaIndex);
    }

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

    public void enrichHostAgendaItems(int presetTypeCode, List<HostAgendaItemDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<HostAgendaItem> coreItems = HostAgendaDtoConverter.toCoreListAligned(items);
        PresetAgendaMergeEngine.enrichHostAgendaItems(
                presetTypeCode,
                presetHostAgendaJson(presetTypeCode),
                coreItems,
                listEnabledSnapshotsByPreset(presetTypeCode));
        for (int i = 0; i < items.size() && i < coreItems.size(); i++) {
            HostAgendaDtoConverter.copyFeishuFields(coreItems.get(i), items.get(i));
        }
    }

    public List<FeishuDocRefDto> listDocRefsForAgenda(Meeting meeting, int agendaIndex) {
        List<FeishuResourceRef> refs = resolveAllResources(meeting, agendaIndex, null);
        return refs.stream().map(FeishuDocRefs::toDto).toList();
    }

    public FeishuResourceRef resolveResource(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        List<FeishuDocRefDto> runtime = null;
        if (runtimeDocUrl != null && !runtimeDocUrl.isBlank()) {
            runtime = List.of(FeishuDocRefDto.builder().url(runtimeDocUrl.trim()).build());
        }
        List<FeishuResourceRef> all = resolveAllResources(meeting, agendaIndex, runtime);
        return all.isEmpty() ? null : all.get(0);
    }

    public List<FeishuResourceRef> resolveAllResources(Meeting meeting, int agendaIndex,
                                                       List<FeishuDocRefDto> runtimeDocs) {
        Integer preset = meeting != null ? meeting.getPresetTypeCode() : null;
        String presetJson = preset != null ? presetHostAgendaJson(preset) : null;
        List<AgendaDocBindingSnapshot> bindings = List.of();
        if (preset != null && preset >= 1 && preset <= 5) {
            bindings = AgendaBindingConverter.fromList(listConfigsForAgenda(preset, agendaIndex));
        }
        return PresetAgendaMergeEngine.resolveAllResources(
                meeting != null ? meeting.getHostAgenda() : null,
                preset,
                presetJson,
                bindings,
                agendaIndex,
                HostAgendaDtoConverter.runtimeDocsFromDto(runtimeDocs));
    }

    public boolean presetTemplateDefinesFeishuForIndex(int presetTypeCode, int agendaIndex) {
        return PresetAgendaMergeEngine.presetTemplateDefinesFeishuForIndex(
                presetHostAgendaJson(presetTypeCode), agendaIndex);
    }

    public MeetingTypePreset getPresetCached(int presetTypeCode) {
        if (presetMapper == null || presetTypeCode < 1 || presetTypeCode > 5) {
            return null;
        }
        return presetCache.getPreset(presetTypeCode, () -> presetMapper.selectById(presetTypeCode));
    }

    public AgendaDocContentResponse buildAgendaDocContent(Meeting meeting, int agendaIndex, String agendaTitle,
                                                          List<FeishuDocRefDto> runtimeDocs) {
        List<FeishuResourceRef> refs = resolveAllResources(meeting, agendaIndex, runtimeDocs);
        AgendaWeeklyReportDto weeklyReport = buildWeeklyReportDto(meeting, agendaIndex).orElse(null);
        if (refs.isEmpty()) {
            if (weeklyReport == null || weeklyReport.getGeneratedReportUrl() == null
                    || weeklyReport.getGeneratedReportUrl().isBlank()) {
                throw new BusinessException(404,
                        "会序 " + (agendaIndex + 1) + (agendaTitle != null && !agendaTitle.isBlank()
                                ? "「" + agendaTitle + "」" : "")
                                + " 未配置飞书资料");
            }
            return AgendaDocContentResponse.builder()
                    .agendaIndex(agendaIndex)
                    .agendaTitle(agendaTitle)
                    .parts(List.of())
                    .weeklyReport(weeklyReport)
                    .build();
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
            if (!anyUrl && (weeklyReport == null || weeklyReport.getPlainText() == null
                    || weeklyReport.getPlainText().isBlank())) {
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
                .weeklyReport(weeklyReport)
                .build();
    }

    Optional<AgendaWeeklyReportDto> buildWeeklyReportDto(Meeting meeting, int agendaIndex) {
        if (meeting == null || meeting.getPresetTypeCode() == null) {
            return Optional.empty();
        }
        return findReportBindingForAgenda(meeting.getPresetTypeCode(), agendaIndex)
                .filter(b -> b.generatedReportUrl() != null && !b.generatedReportUrl().isBlank())
                .map(b -> {
                    String url = b.generatedReportUrl().trim();
                    AgendaWeeklyReportDto.AgendaWeeklyReportDtoBuilder builder = AgendaWeeklyReportDto.builder()
                            .generatedReportUrl(url);
                    if (b.generatedReportAt() != null) {
                        builder.generatedReportAt(b.generatedReportAt().toString());
                    }
                    FeishuResourceRef ref = FeishuResourceResolver.resolve(url);
                    if (ref == null || ref.kind() == FeishuResourceKind.UNKNOWN || !ref.canFetchPlainText()) {
                        builder.fetchError("无法内嵌拉取通报正文，请点击下方链接在飞书中打开");
                        return builder.build();
                    }
                    try {
                        String text = feishuService.fetchResourcePlainText(ref);
                        if (text != null && !text.isBlank()) {
                            builder.plainText(text.trim());
                        } else {
                            builder.fetchError("通报 Doc 已读取但正文为空");
                        }
                    } catch (BusinessException e) {
                        builder.fetchError(e.getMessage());
                    } catch (Exception e) {
                        log.warn("fetch weekly report agendaIndex={}: {}", agendaIndex, e.getMessage());
                        builder.fetchError("拉取通报失败: " + e.getMessage());
                    }
                    return builder.build();
                });
    }

    public String resolveFeishuDocUrl(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        FeishuResourceRef ref = resolveResource(meeting, agendaIndex, runtimeDocUrl);
        return ref != null ? ref.defaultOpenUrl() : null;
    }

    public String resolveDocumentId(Meeting meeting, int agendaIndex, String runtimeDocUrl) {
        FeishuResourceRef ref = resolveResource(meeting, agendaIndex, runtimeDocUrl);
        return ref != null ? ref.primaryToken() : null;
    }

    public List<HostAgendaItemDto> parseHostAgendaItemsPublic(String hostAgendaJson) {
        return HostAgendaDtoConverter.toDtoList(PresetAgendaMergeEngine.parseHostAgendaItems(objectMapper, hostAgendaJson));
    }

    public static boolean hostAgendaItemHasFeishu(HostAgendaItemDto item) {
        return PresetAgendaMergeEngine.hostAgendaItemHasFeishu(HostAgendaDtoConverter.toCore(item));
    }

    private List<AgendaDocBindingSnapshot> listEnabledSnapshotsByPreset(int presetTypeCode) {
        return AgendaBindingConverter.fromList(listEnabledByPreset(presetTypeCode));
    }

    private String presetHostAgendaJson(int presetTypeCode) {
        MeetingTypePreset preset = getPresetCached(presetTypeCode);
        return preset != null ? preset.getHostAgenda() : null;
    }

    private static LambdaQueryWrapper<MatterProgressDocConfig> enabledPresetQuery(int presetTypeCode) {
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1)
                .eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .isNotNull(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        return q;
    }
}
