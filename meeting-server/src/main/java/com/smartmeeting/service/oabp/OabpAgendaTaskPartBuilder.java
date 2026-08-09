package com.smartmeeting.service.oabp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.AgendaDocPartDto;
import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import com.smartmeeting.config.oabp.OabpSheetDisplayMeta;
import com.smartmeeting.config.oabp.OabpSheetTemplateEngine;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code meeting.host_agenda} 读取 {@code oabpTaskSql}，查询 oabp 库并生成会序资料 part。
 */
@Slf4j
@Service
public class OabpAgendaTaskPartBuilder {

    private static final String DOC_KIND = "OABP";
    private static final String CONTENT_TYPE = "sheet_cells";

    private final ObjectMapper objectMapper;
    private final OabpAgendaTaskQueryService queryService;

    /**
     * @param objectMapper  解析 host_agenda JSON
     * @param queryService  oabp 查询服务；未启用 oabp 时为 null
     */
    public OabpAgendaTaskPartBuilder(
            ObjectMapper objectMapper,
            @Autowired(required = false) OabpAgendaTaskQueryService queryService) {
        this.objectMapper = objectMapper;
        this.queryService = queryService;
    }

    /**
     * 构建指定会序的 oabp 项目任务资料 part（0 或 1 条）。
     *
     * @param meeting     会议实体
     * @param agendaIndex 会序下标
     * @return 含 structuredContent 或 fetchError 的 part 列表；未配置 SQL 时为空列表
     */
    public List<AgendaDocPartDto> buildParts(Meeting meeting, int agendaIndex) {
        return buildParts(meeting, agendaIndex, null);
    }

    /**
     * @param presetHostAgendaJson 可选 preset 模板 JSON；会议快照无 oabpTaskSql 时回退读取
     */
    public List<AgendaDocPartDto> buildParts(Meeting meeting, int agendaIndex, String presetHostAgendaJson) {
        String sql = resolveOabpTaskSql(meeting, agendaIndex, presetHostAgendaJson);
        if (sql == null || sql.isBlank()) {
            return List.of();
        }
        if (!isOabpTaskShowEnabled(meeting, agendaIndex, presetHostAgendaJson)) {
            return List.of();
        }
        if (queryService == null) {
            return List.of(disabledPart());
        }
        try {
            SheetStructuredDto sheet = queryService.queryAsSheet(sql);
            if (isOabpTaskSqlStrict(meeting, agendaIndex, presetHostAgendaJson)) {
                sheet.setDisplayMeta(OabpSheetDisplayMeta.builder().sqlStrict(true).build());
            } else {
                OabpDisplayTemplate template = resolveOabpDisplayTemplate(meeting, agendaIndex, presetHostAgendaJson);
                if (template != null && !template.isEmpty()) {
                    sheet = OabpSheetDataMapper.toDto(
                            OabpSheetTemplateEngine.apply(OabpSheetDataMapper.fromDto(sheet), template));
                }
            }
            return List.of(AgendaDocPartDto.builder()
                    .docKind(DOC_KIND)
                    .contentType(CONTENT_TYPE)
                    .structuredContent(sheet)
                    .build());
        } catch (BusinessException e) {
            log.warn("oabp agenda part agendaIndex={}: {}", agendaIndex, e.getMessage());
            return List.of(AgendaDocPartDto.builder()
                    .docKind(DOC_KIND)
                    .fetchError(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.warn("oabp agenda part agendaIndex={}: {}", agendaIndex, e.getMessage());
            return List.of(AgendaDocPartDto.builder()
                    .docKind(DOC_KIND)
                    .fetchError("oabp 查询失败: " + e.getMessage())
                    .build());
        }
    }

    private String resolveOabpTaskSql(Meeting meeting, int agendaIndex, String presetHostAgendaJson) {
        String fromMeeting = oabpTaskSqlFromHostAgenda(
                meeting != null ? meeting.getHostAgenda() : null, agendaIndex);
        if (fromMeeting != null && !fromMeeting.isBlank()) {
            return fromMeeting.strip();
        }
        return oabpTaskSqlFromHostAgenda(presetHostAgendaJson, agendaIndex);
    }

    private String oabpTaskSqlFromHostAgenda(String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return null;
        }
        List<HostAgendaItem> items = PresetAgendaMergeEngine.parseHostAgendaItems(
                objectMapper, hostAgendaJson);
        if (agendaIndex < 0 || agendaIndex >= items.size()) {
            return null;
        }
        HostAgendaItem item = items.get(agendaIndex);
        if (item == null || item.getOabpTaskSql() == null) {
            return null;
        }
        return item.getOabpTaskSql().strip();
    }

    private boolean isOabpTaskShowEnabled(Meeting meeting, int agendaIndex, String presetHostAgendaJson) {
        Boolean fromMeeting = oabpTaskShowFromHostAgenda(
                meeting != null ? meeting.getHostAgenda() : null, agendaIndex);
        if (fromMeeting != null) {
            return fromMeeting;
        }
        Boolean fromPreset = oabpTaskShowFromHostAgenda(presetHostAgendaJson, agendaIndex);
        return fromPreset == null || fromPreset;
    }

    private Boolean oabpTaskShowFromHostAgenda(String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return null;
        }
        List<HostAgendaItem> items = PresetAgendaMergeEngine.parseHostAgendaItems(
                objectMapper, hostAgendaJson);
        if (agendaIndex < 0 || agendaIndex >= items.size()) {
            return null;
        }
        HostAgendaItem item = items.get(agendaIndex);
        if (item == null || item.getOabpTaskSql() == null || item.getOabpTaskSql().isBlank()) {
            return null;
        }
        return item.getOabpTaskShow() == null || item.getOabpTaskShow();
    }

    private OabpDisplayTemplate resolveOabpDisplayTemplate(
            Meeting meeting, int agendaIndex, String presetHostAgendaJson) {
        OabpDisplayTemplate fromMeeting = oabpDisplayTemplateFromHostAgenda(
                meeting != null ? meeting.getHostAgenda() : null, agendaIndex);
        if (fromMeeting != null && !fromMeeting.isEmpty()) {
            return fromMeeting;
        }
        return oabpDisplayTemplateFromHostAgenda(presetHostAgendaJson, agendaIndex);
    }

    private boolean isOabpTaskSqlStrict(Meeting meeting, int agendaIndex, String presetHostAgendaJson) {
        Boolean fromMeeting = oabpTaskSqlStrictFromHostAgenda(
                meeting != null ? meeting.getHostAgenda() : null, agendaIndex);
        if (fromMeeting != null) {
            return fromMeeting;
        }
        Boolean fromPreset = oabpTaskSqlStrictFromHostAgenda(presetHostAgendaJson, agendaIndex);
        return fromPreset != null && fromPreset;
    }

    private Boolean oabpTaskSqlStrictFromHostAgenda(String hostAgendaJson, int agendaIndex) {
        HostAgendaItem item = itemAt(hostAgendaJson, agendaIndex);
        if (item == null || item.getOabpTaskSql() == null || item.getOabpTaskSql().isBlank()) {
            return null;
        }
        return item.getOabpTaskSqlStrict();
    }

    private OabpDisplayTemplate oabpDisplayTemplateFromHostAgenda(String hostAgendaJson, int agendaIndex) {
        HostAgendaItem item = itemAt(hostAgendaJson, agendaIndex);
        if (item == null) {
            return null;
        }
        return item.getOabpDisplayTemplate();
    }

    private HostAgendaItem itemAt(String hostAgendaJson, int agendaIndex) {
        if (hostAgendaJson == null || hostAgendaJson.isBlank()) {
            return null;
        }
        List<HostAgendaItem> items = PresetAgendaMergeEngine.parseHostAgendaItems(
                objectMapper, hostAgendaJson);
        if (agendaIndex < 0 || agendaIndex >= items.size()) {
            return null;
        }
        return items.get(agendaIndex);
    }

    private static AgendaDocPartDto disabledPart() {
        return AgendaDocPartDto.builder()
                .docKind(DOC_KIND)
                .fetchError("oabp 数据源未启用（MEETING_DB_OABP_ENABLED=false）")
                .build();
    }

    /**
     * 合并 local 与 oabp parts，保持顺序：local 在前。
     *
     * @param localParts 本地上传资料 parts
     * @param oabpParts  oabp 查询 parts
     * @return 合并后的新列表
     */
    public static List<AgendaDocPartDto> mergeLocalAndOabp(
            List<AgendaDocPartDto> localParts, List<AgendaDocPartDto> oabpParts) {
        List<AgendaDocPartDto> merged = new ArrayList<>();
        if (localParts != null) {
            merged.addAll(localParts);
        }
        if (oabpParts != null) {
            merged.addAll(oabpParts);
        }
        return merged;
    }

    /**
     * 是否存在 oabp 配置或结果 part（含 fetchError，用于避免误报 404）。
     *
     * @param oabpParts {@link #buildParts} 返回值
     * @return 非空列表时 true
     */
    public static boolean hasOabpParts(List<AgendaDocPartDto> oabpParts) {
        return oabpParts != null && !oabpParts.isEmpty();
    }
}
