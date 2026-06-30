package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.AgendaDocContentResponse;
import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.AgendaWeeklyReportDto;
import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaDocRoleRules;
import com.smartmeeting.config.agenda.AgendaReportBinding;
import com.smartmeeting.config.agenda.HostAgendaDtoConverter;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.config.agenda.HostAgendaFeishuDocRef;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.config.feishu.FeishuResourceResolver;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.service.cache.MeetingPresetCacheService;
import com.smartmeeting.service.cache.PresetBundle;
import com.smartmeeting.service.feishu.FeishuDocRefs;
import com.smartmeeting.api.dto.structured.BitableStructuredDto;
import com.smartmeeting.api.dto.structured.TaskListStructuredDto;
import com.smartmeeting.api.dto.structured.ExcelWorkbookDto;
import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.service.structured.LocalAgendaMaterialPartBuilder;
import com.smartmeeting.service.structured.StructuredImageCollector;
import com.smartmeeting.service.oabp.OabpAgendaTaskPartBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 预设会序飞书资料绑定服务（应用层）。
 * <p>
 * 资料权威存储为 {@code int_meeting_type_preset.host_agenda} v2；合并委托 {@link PresetAgendaMergeEngine}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresetAgendaDocService {

    private final MeetingTypePresetMapper presetMapper;
    private final MeetingPresetCacheService presetCache;
    private final FeishuService feishuService;
    private final AgendaMaterialStorageService agendaMaterialStorageService;
    private final ObjectMapper objectMapper;
    private final OabpAgendaTaskPartBuilder oabpAgendaTaskPartBuilder;
    private final com.smartmeeting.service.weekly.WeeklyComparisonReportQueryService weeklyComparisonReportQueryService;

    public PresetBundle refreshPresetBundle(int presetTypeCode) {
        if (presetTypeCode <= 0) {
            return new PresetBundle(null);
        }
        MeetingTypePreset preset = presetMapper.selectById(presetTypeCode);
        return presetCache.putPresetBundle(presetTypeCode, preset);
    }

    public void refreshAllPresetBundles() {
        if (presetMapper == null) {
            return;
        }
        presetMapper.selectList(null).stream()
                .map(MeetingTypePreset::getCode)
                .filter(code -> code != null && code > 0)
                .forEach(code -> {
                    try {
                        refreshPresetBundle(code);
                    } catch (Exception e) {
                        log.warn("refreshPresetBundle code={} failed: {}", code, e.getMessage());
                    }
                });
    }

    public String syncHostAgendaForCreate(int presetTypeCode, List<HostAgendaItemDto> requestItems) {
        if (presetTypeCode <= 0) {
            return PresetAgendaMergeEngine.toHostAgendaJson(objectMapper, HostAgendaDtoConverter.toCoreList(requestItems));
        }
        refreshPresetBundle(presetTypeCode);
        String presetJson = presetHostAgendaJson(presetTypeCode);
        return PresetAgendaMergeEngine.syncHostAgendaJson(
                objectMapper,
                presetTypeCode,
                presetJson,
                HostAgendaDtoConverter.toCoreList(requestItems),
                List.of());
    }

    public String toHostAgendaJson(List<HostAgendaItemDto> items) {
        return PresetAgendaMergeEngine.toHostAgendaJson(objectMapper, HostAgendaDtoConverter.toCoreList(items));
    }

    public List<AgendaDocBindingSnapshot> listEnabledByPreset(int presetTypeCode) {
        if (presetTypeCode <= 0) {
            return List.of();
        }
        return PresetAgendaMergeEngine.filterSourceBindings(
                PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                        presetTypeCode, presetHostAgendaJson(presetTypeCode), objectMapper));
    }

    public Optional<AgendaReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode <= 0 || agendaIndex < 0) {
            return Optional.empty();
        }
        return PresetAgendaMergeEngine.findReportBindingInHostAgenda(
                presetHostAgendaJson(presetTypeCode), agendaIndex, objectMapper);
    }

    public static boolean isSourceRoleForMerge(AgendaDocBindingSnapshot cfg) {
        return AgendaDocRoleRules.isSourceRoleForMerge(cfg);
    }

    public List<AgendaDocBindingSnapshot> listConfigsForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode <= 0 || agendaIndex < 0) {
            return List.of();
        }
        return PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                        presetTypeCode, presetHostAgendaJson(presetTypeCode), objectMapper).stream()
                .filter(b -> b.getAgendaIndex() != null && b.getAgendaIndex() == agendaIndex)
                .filter(b -> b.getEnabled() != null && b.getEnabled() == 1)
                .toList();
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
                List.of());
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
        if (preset != null && preset > 0) {
            bindings = listConfigsForAgenda(preset, agendaIndex);
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
        if (presetMapper == null || presetTypeCode <= 0) {
            return null;
        }
        return presetCache.getPreset(presetTypeCode, () -> presetMapper.selectById(presetTypeCode));
    }

    public AgendaDocContentResponse buildAgendaDocContent(Meeting meeting, int agendaIndex, String agendaTitle,
                                                          List<FeishuDocRefDto> runtimeDocs) {
        List<FeishuResourceRef> refs = resolveAllResources(meeting, agendaIndex, runtimeDocs);
        AgendaWeeklyReportDto weeklyReport = buildWeeklyReportDto(meeting, agendaIndex).orElse(null);
        List<AgendaDocPartDto> localParts = LocalAgendaMaterialPartBuilder.buildParts(
                meeting, agendaIndex, objectMapper, agendaMaterialStorageService);
        Integer presetCode = meeting != null ? meeting.getPresetTypeCode() : null;
        String presetAgendaJson = presetCode != null && presetCode > 0 ? presetHostAgendaJson(presetCode) : null;
        List<AgendaDocPartDto> oabpParts = oabpAgendaTaskPartBuilder.buildParts(
                meeting, agendaIndex, presetAgendaJson);
        List<AgendaDocPartDto> materialParts = OabpAgendaTaskPartBuilder.mergeLocalAndOabp(localParts, oabpParts);
        if (refs.isEmpty()) {
            boolean hasWeekly = weeklyReport != null && weeklyReport.getGeneratedReportUrl() != null
                    && !weeklyReport.getGeneratedReportUrl().isBlank();
            if (materialParts.isEmpty() && !hasWeekly) {
                if (!hasAgendaMaterialConfigured(meeting, agendaIndex, presetAgendaJson)) {
                    throw new BusinessException(404,
                            "会序 " + (agendaIndex + 1) + (agendaTitle != null && !agendaTitle.isBlank()
                                    ? "「" + agendaTitle + "」" : "")
                                    + " 未配置资料");
                }
            }
            return AgendaDocContentResponse.builder()
                    .agendaIndex(agendaIndex)
                    .agendaTitle(agendaTitle)
                    .parts(materialParts)
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
                    if (!applyStructuredFeishuPart(partBuilder, ref, meeting)) {
                        String text = feishuService.fetchResourcePlainText(ref);
                        if (text != null && !text.isBlank()) {
                            partBuilder.plainText(text);
                            combined.append('【').append(FeishuDocRefs.kindLabel(ref.kind())).append("】\n")
                                    .append(text.trim()).append("\n\n");
                        } else {
                            partBuilder.fetchError("资料已读取但正文为空");
                        }
                    }
                } catch (BusinessException e) {
                    partBuilder.fetchError(e.getMessage());
                } catch (Exception e) {
                    log.warn("fetch part agendaIndex={} kind={}: {}", agendaIndex, ref.kind(), e.getMessage());
                    partBuilder.fetchError("拉取失败: " + e.getMessage());
                }
            } else {
                partBuilder.fetchError("无法内嵌拉取正文，请使用下方链接在飞书中打开");
            }
            parts.add(partBuilder.build());
        }
        parts.addAll(materialParts);
        boolean anyText = parts.stream().anyMatch(p -> p.getPlainText() != null && !p.getPlainText().isBlank());
        boolean anyStructured = parts.stream().anyMatch(p -> p.getStructuredContent() != null);
        if (!anyText && !anyStructured && parts.stream().allMatch(p -> p.getFetchError() != null)) {
            boolean anyUrl = parts.stream().anyMatch(p -> p.getFeishuDocUrl() != null && !p.getFeishuDocUrl().isBlank());
            boolean hasWeeklyText = weeklyReport != null && weeklyReport.getPlainText() != null
                    && !weeklyReport.getPlainText().isBlank();
            boolean hasWeeklyStructured = weeklyReport != null && weeklyReport.getStructuredContent() != null;
            if (!anyUrl && !hasWeeklyText && !hasWeeklyStructured && !OabpAgendaTaskPartBuilder.hasOabpParts(oabpParts)) {
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
                .filter(b -> b.hasAnyReport())
                .map(b -> {
                    // v0.26：优先 runId → 读 run + items
                    if (b.generatedReportRunId() != null) {
                        return buildFromRun(b.generatedReportRunId());
                    }
                    // 过渡期 fallback：旧 generatedReportUrl → 飞书拉取
                    return buildFromLegacyUrl(b, meeting, agendaIndex);
                });
    }

    private AgendaWeeklyReportDto buildFromRun(long runId) {
        return weeklyComparisonReportQueryService.findRunById(runId)
                .map(run -> {
                    var items = weeklyComparisonReportQueryService.findItemsByRunId(runId);
                    String genAt = run.getGeneratedAt() != null ? run.getGeneratedAt().toString() : null;
                    AgendaWeeklyReportDto.AgendaWeeklyReportDtoBuilder builder = AgendaWeeklyReportDto.builder()
                            .runId(runId)
                            .title(run.getTitle())
                            .itemCount(run.getItemCount())
                            .generationStatus(run.getGenerationStatus())
                            .items(items)
                            .generatedReportAt(genAt);
                    if ("FAILED".equals(run.getGenerationStatus()) || "PARTIAL".equals(run.getGenerationStatus())) {
                        builder.fetchError(run.getRunError());
                    }
                    return builder.build();
                })
                .orElseGet(() -> AgendaWeeklyReportDto.builder()
                        .runId(runId)
                        .fetchError("run id=" + runId + " 不存在")
                        .build());
    }

    private AgendaWeeklyReportDto buildFromLegacyUrl(AgendaReportBinding b, Meeting meeting, int agendaIndex) {
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
            AgendaDocPartDto.AgendaDocPartDtoBuilder partScratch = AgendaDocPartDto.builder();
            if (applyStructuredFeishuPart(partScratch, ref, meeting)) {
                AgendaDocPartDto part = partScratch.build();
                builder.contentType(part.getContentType());
                builder.structuredContent(part.getStructuredContent());
            } else {
                String text = feishuService.fetchResourcePlainText(ref);
                if (text != null && !text.isBlank()) {
                    builder.plainText(text.trim());
                } else {
                    builder.fetchError("通报 Doc 已读取但正文为空");
                }
            }
        } catch (BusinessException e) {
            builder.fetchError(e.getMessage());
        } catch (Exception e) {
            log.warn("fetch weekly report agendaIndex={}: {}", agendaIndex, e.getMessage());
            builder.fetchError("拉取通报失败: " + e.getMessage());
        }
        return builder.build();
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

    private String presetHostAgendaJson(int presetTypeCode) {
        MeetingTypePreset preset = getPresetCached(presetTypeCode);
        return preset != null ? preset.getHostAgenda() : null;
    }

    /**
     * 优先结构化拉取；成功则跳过 plainText 二次飞书请求。
     *
     * @return true 表示已填充 structured，调用方无需再拉 plainText
     */
    private boolean applyStructuredFeishuPart(AgendaDocPartDto.AgendaDocPartDtoBuilder partBuilder,
                                            FeishuResourceRef ref, Meeting meeting) {
        try {
            String contentType = feishuService.resolveStructuredContentType(ref);
            Object structured = feishuService.fetchResourceStructuredContent(ref);
            if (structured instanceof com.smartmeeting.api.dto.structured.ExcelWorkbookDto) {
                contentType = "excel_workbook";
            }
            if (contentType == null || structured == null) {
                return false;
            }
            if (!isUsefulStructuredContent(structured, contentType)) {
                log.warn("structured content empty kind={} type={}, fallback plainText", ref.kind(), contentType);
                return false;
            }
            partBuilder.contentType(contentType);
            partBuilder.structuredContent(structured);
            String rasterCacheKey = feishuService.resolveRasterCacheKey(ref);
            if (rasterCacheKey != null && !rasterCacheKey.isBlank()) {
                partBuilder.rasterCacheKey(rasterCacheKey);
            }
            if ("docx_blocks".equals(contentType) && meeting != null && meeting.getId() != null) {
                var images = StructuredImageCollector.collectFromStructuredJson(
                        objectMapper, structured, contentType, meeting.getId());
                if (!images.isEmpty()) {
                    partBuilder.images(images);
                }
            }
            return true;
        } catch (Exception se) {
            log.warn("structured export failed kind={}: {}", ref.kind(), se.getMessage());
            return false;
        }
    }

    private static boolean isUsefulStructuredContent(Object structured, String contentType) {
        if (structured instanceof SheetStructuredDto sheet) {
            boolean noRows = sheet.getRows() == null || sheet.getRows().isEmpty();
            boolean noHeaders = sheet.getHeaders() == null || sheet.getHeaders().isEmpty()
                    || sheet.getHeaders().stream().allMatch(h -> h == null || h.isBlank());
            if ("无数据".equals(sheet.getSheetName()) && noRows && noHeaders) {
                return false;
            }
            return !(noRows && noHeaders);
        }
        if (structured instanceof BitableStructuredDto bitable) {
            boolean noCols = bitable.getColumns() == null || bitable.getColumns().isEmpty();
            boolean noRecords = bitable.getTotalRecords() <= 0
                    && (bitable.getRecords() == null || bitable.getRecords().isEmpty());
            boolean noTables = bitable.getTables() == null || bitable.getTables().isEmpty();
            return !(noCols && noRecords && noTables);
        }
        if (structured instanceof TaskListStructuredDto taskList) {
            boolean hasName = taskList.getTasklistName() != null && !taskList.getTasklistName().isBlank();
            boolean hasItems = taskList.getItems() != null && !taskList.getItems().isEmpty();
            return hasName || hasItems;
        }
        if (structured instanceof ExcelWorkbookDto wb) {
            return wb.getSheets() != null && !wb.getSheets().isEmpty();
        }
        if (structured instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    /**
     * 会序是否配置了任意资料（含主持页隐藏的），用于区分「未配置」与「全部隐藏」。
     */
    private boolean hasAgendaMaterialConfigured(Meeting meeting, int agendaIndex, String presetAgendaJson) {
        if (itemHasMaterialContent(HostAgendaJsonCodec.parseItemAtIndex(
                objectMapper, meeting != null ? meeting.getHostAgenda() : null, agendaIndex))) {
            return true;
        }
        return itemHasMaterialContent(HostAgendaJsonCodec.parseItemAtIndex(
                objectMapper, presetAgendaJson, agendaIndex));
    }

    private static boolean itemHasMaterialContent(HostAgendaItem item) {
        if (item == null) {
            return false;
        }
        if (item.getOabpTaskSql() != null && !item.getOabpTaskSql().isBlank()) {
            return true;
        }
        if (item.getDocs() != null) {
            for (HostAgendaDocBinding doc : item.getDocs()) {
                if (doc == null || !doc.isEnabled()) {
                    continue;
                }
                if (doc.isLocalStorage()) {
                    if (doc.getFileId() != null && !doc.getFileId().isBlank()) {
                        return true;
                    }
                } else if (doc.getUrl() != null && !doc.getUrl().isBlank()) {
                    return true;
                }
            }
        }
        if (item.getFeishuDocUrl() != null && !item.getFeishuDocUrl().isBlank()) {
            return true;
        }
        return item.getFeishuDocs() != null && !item.getFeishuDocs().isEmpty();
    }
}
