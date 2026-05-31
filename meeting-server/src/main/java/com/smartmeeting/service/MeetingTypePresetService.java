package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 会议类型预设服务。
 *
 * <p>管理 {@code int_meeting_type_preset} 中的标准会议类型（code 1～6），
 * 提供列表查询、创建会议时的字段合并，以及主持议题项的解析与飞书文档补全。
 *
 * <p>主要协作组件：
 * <ul>
 *   <li>{@link MeetingTypePresetMapper} — 预设持久化</li>
 *   <li>{@link PresetAgendaDocService} — 主持议题关联飞书资料补全</li>
 *   <li>{@link ObjectMapper} — 预设 JSON 中 {@code hostAgenda} 解析</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MeetingTypePresetService {

    /** 预设未指定集团时的默认集团名称。 */
    public static final String DEFAULT_COMPANY = "吉青汽车科技集团";
    private final MeetingTypePresetMapper presetMapper;
    private final ObjectMapper objectMapper;
    private final PresetAgendaDocService presetAgendaDocService;

    /**
     * 列出全部会议类型预设，按 code 升序。
     *
     * @return 预设响应 DTO 列表
     */
    public List<MeetingPresetResponse> listPresets() {
        return presetMapper.selectList(null).stream()
                .sorted((a, b) -> Integer.compare(a.getCode(), b.getCode()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将实体转换为 API 响应 DTO。
     *
     * @param p 会议类型预设实体
     * @return 含展示名、组织信息、参会人列表及主持议题 JSON 的响应
     */
    public MeetingPresetResponse toResponse(MeetingTypePreset p) {
        return MeetingPresetResponse.builder()
                .code(p.getCode())
                .displayName(p.getDisplayName())
                .company(p.getCompany())
                .department(p.getDepartment())
                .groupName(p.getGroupName())
                .scheduleNote(p.getScheduleNote())
                .agendaSummary(p.getAgendaSummary())
                .organizerName(p.getOrganizerName())
                .leaderName(p.getLeaderName())
                .participantNames(splitNames(p.getParticipantsNames()))
                .hostAgenda(p.getHostAgenda())
                .build();
    }

    /**
     * 按 {@link MeetingCreateRequest#getPresetTypeCode()} 将预设字段合并到创建请求（会修改 request）。
     *
     * <p>所有 {@code presetTypeCode>0} 均按模板会处理：从库加载完整预设
     * （标题、议程、参会人、主持议题等），不再区分「其他会议」手填主题模式。
     *
     * @param request 会议创建请求，{@code presetTypeCode} 为 null 时不做处理
     * @throws BusinessException {@code presetTypeCode} 非法或对应预设不存在
     */
    public void mergeIntoCreateRequest(MeetingCreateRequest request) {
        Integer code = request.getPresetTypeCode();
        if (code == null) {
            return;
        }
        if (code <= 0) {
            throw new BusinessException(400, "presetTypeCode 必须为正整数");
        }
        MeetingTypePreset p = presetAgendaDocService.getPresetCached(code);
        if (p == null) {
            throw new BusinessException(400, "未找到会议类型预设: " + code);
        }
        request.setTitle(p.getDisplayName());
        request.setCompany(p.getCompany());
        request.setDepartment(p.getDepartment());
        request.setGroupName(p.getGroupName());
        List<String> agenda = new ArrayList<>();
        if (p.getScheduleNote() != null && !p.getScheduleNote().isBlank()) {
            agenda.add("召开时间：" + p.getScheduleNote());
        }
        if (p.getAgendaSummary() != null && !p.getAgendaSummary().isBlank()) {
            agenda.add("会议内容：" + p.getAgendaSummary());
        }
        if (p.getOrganizerName() != null && !p.getOrganizerName().isBlank()) {
            agenda.add("组织人：" + p.getOrganizerName());
        }
        if (p.getLeaderName() != null && !p.getLeaderName().isBlank()) {
            agenda.add("会议主导：" + p.getLeaderName());
        }
        request.setAgenda(agenda);

        List<MeetingCreateRequest.ParticipantEntry> entries = new ArrayList<>();
        for (String name : splitNames(p.getParticipantsNames())) {
            if (name.isEmpty()) {
                continue;
            }
            MeetingCreateRequest.ParticipantEntry e = new MeetingCreateRequest.ParticipantEntry();
            e.setName(name);
            e.setUserId("vp_" + UUID.randomUUID().toString().replace("-", ""));
            entries.add(e);
        }
        request.setParticipants(entries);
        // 不在此阶段回填 hostAgendaItems，避免把模板 host_agenda(v2 docs[])降级成旧字段后再写回 meeting 快照。
        // createMeeting 中的 syncHostAgendaForCreate 会直接基于最新 preset.host_agenda 生成会议快照。
    }

    /**
     * 按预设 code 返回主持议题项列表（与 merge 解析逻辑一致，供会议详情 API 补全）。
     *
     * @param code 预设类型码（正整数有效）
     * @return 主持议题 DTO 列表；code 非法、无预设或 JSON 为空时返回 {@code null}
     */
    public List<HostAgendaItemDto> hostAgendaItemsForPresetCode(int code) {
        if (code <= 0) {
            return null;
        }
        MeetingTypePreset p = presetAgendaDocService.getPresetCached(code);
        if (p == null) {
            return null;
        }
        List<HostAgendaItemDto> items = hostAgendaItemsFromPresetJson(p.getHostAgenda());
        if (items != null && !items.isEmpty()) {
            presetAgendaDocService.enrichHostAgendaItems(code, items);
        }
        return items;
    }

    /**
     * 从预设 {@code hostAgenda} JSON 的 {@code items} 数组解析主持议题项。
     */
    private List<HostAgendaItemDto> hostAgendaItemsFromPresetJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return null;
            }
            List<HostAgendaItemDto> out = new ArrayList<>();
            for (JsonNode n : items) {
                String title = n.path("title").asText("").trim();
                if (title.isEmpty()) {
                    continue;
                }
                HostAgendaItemDto dto = new HostAgendaItemDto();
                dto.setTitle(title);
                dto.setMinutes(n.path("minutes").asInt(10));
                String detail = n.path("detail").asText("").trim();
                if (!detail.isEmpty()) {
                    dto.setDetail(detail);
                }
                String docUrl = n.path("feishuDocUrl").asText("").trim();
                if (docUrl.isEmpty()) {
                    String legacyId = n.path("feishuDocToken").asText("").trim();
                    docUrl = com.smartmeeting.config.feishu.FeishuResourceResolver.legacyDocIdToDocxUrl(legacyId);
                    if (docUrl == null) {
                        docUrl = "";
                    }
                }
                if (!docUrl.isEmpty()) {
                    dto.setFeishuDocUrl(docUrl);
                }
                out.add(dto);
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将参会人姓名字符串按顿号、逗号、分号、空白及「以及」等分隔为列表。
     *
     * @param raw 原始姓名字符串，可为 null
     * @return 去空白后的姓名列表；null 或空白输入返回空列表
     */
    public List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String s = raw.replace("以及", ",").replace("及指定相关人员", "").trim();
        return Arrays.stream(s.split("[、,，;；\\s]+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}
