package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.agenda.MatterDocConfigAgendaConfigProvider;
import com.smartmeeting.admin.agenda.PresetTableAgendaConfigProvider;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.api.dto.MatterConfigOptionDto;
import com.smartmeeting.admin.entity.MatterProgressDocConfig;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.config.agenda.AgendaConfigProviderRegistry;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgendaConfigService {

    private final AgendaConfigProviderRegistry registry;
    private final PresetTableAgendaConfigProvider presetProvider;
    private final MatterDocConfigAgendaConfigProvider docProvider;
    private final MatterProgressDocConfigMapper matterConfigMapper;
    private final HostAgendaJsonHelper hostAgendaJsonHelper;
    private final MeetingServerBridgeService meetingServerBridge;

    public List<String> listProviderIds() {
        return registry.providerIds();
    }

    public AgendaPresetSnapshot loadBundle(int presetTypeCode) {
        AgendaPresetSnapshot preset = presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found: " + presetTypeCode));
        List<AgendaDocBindingSnapshot> docs = docProvider.listBindings(presetTypeCode);
        preset.setDocBindings(docs);
        return preset;
    }

    public void savePreset(AgendaPresetSnapshot snapshot) {
        if (snapshot.getHostAgendaJson() != null && !snapshot.getHostAgendaJson().isBlank()) {
            hostAgendaJsonHelper.parseItems(snapshot.getHostAgendaJson());
        }
        presetProvider.savePreset(snapshot);
        meetingServerBridge.refreshPresetCache(snapshot.getPresetTypeCode());
    }

    public List<HostAgendaItemRowDto> parseAgendaItems(String hostAgendaJson) {
        return hostAgendaJsonHelper.parseItems(hostAgendaJson);
    }

    public List<String> validateAgenda(String hostAgendaJson) {
        return hostAgendaJsonHelper.validate(hostAgendaJson);
    }

    public void saveAgendaItems(int presetTypeCode, List<HostAgendaItemRowDto> items) {
        AgendaPresetSnapshot snap = presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found"));
        snap.setHostAgendaJson(hostAgendaJsonHelper.toJson(items));
        savePreset(snap);
    }

    /**
     * 经 meeting-server internal API 写回未开始会议（含 doc 飞书 enrich）。
     */
    public Map<String, Object> refreshUnstartedMeetingsHostAgenda(int presetTypeCode, boolean dryRun) {
        presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found"));
        return meetingServerBridge.refreshHostAgendaEnriched(presetTypeCode, dryRun, null);
    }

    public void refreshPresetCache(int presetTypeCode) {
        meetingServerBridge.refreshPresetCache(presetTypeCode);
    }

    public List<AgendaDocBindingSnapshot> listDocBindings(int presetTypeCode) {
        return docProvider.listBindings(presetTypeCode);
    }

    public long saveDocBinding(AgendaDocBindingSnapshot snap) {
        if (snap.getPresetTypeCode() == null || snap.getPresetTypeCode() < 1 || snap.getPresetTypeCode() > 5) {
            throw new BusinessException("invalid presetTypeCode");
        }
        return docProvider.saveBinding(snap);
    }

    public void deleteDocBinding(long id) {
        docProvider.deleteBinding(id);
    }

    /**
     * 合并预览：preset host_agenda + doc 绑定摘要（不拉飞书正文）。
     */
    public List<String> previewMergeLines(int presetTypeCode) {
        AgendaPresetSnapshot bundle = loadBundle(presetTypeCode);
        List<String> lines = new ArrayList<>();
        lines.add("preset=" + bundle.getDisplayName() + " code=" + presetTypeCode);
        if (bundle.getHostAgendaJson() != null && !bundle.getHostAgendaJson().isBlank()) {
            lines.add("host_agenda: " + bundle.getHostAgendaJson());
        }
        if (bundle.getDocBindings() != null) {
            for (AgendaDocBindingSnapshot d : bundle.getDocBindings()) {
                lines.add(String.format("doc[%d] idx=%s slot=%s role=%s name=%s url=%s",
                        d.getId(), d.getAgendaIndex(), d.getResourceSlot(), d.getConfigRole(),
                        d.getConfigName(), d.getFeishuDocUrl()));
            }
        }
        return lines;
    }

    /**
     * 对比任务编辑：按 role 筛选 matter 配置名。
     */
    public List<MatterConfigOptionDto> listMatterConfigOptions(String role) {
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1);
        if (role != null && !role.isBlank()) {
            String r = role.toUpperCase();
            if ("SOURCE".equals(r)) {
                q.in(MatterProgressDocConfig::getConfigRole, List.of("SOURCE", "BOTH"));
            } else if ("OUTPUT".equals(r)) {
                q.in(MatterProgressDocConfig::getConfigRole, List.of("OUTPUT", "BOTH"));
            }
        }
        q.orderByAsc(MatterProgressDocConfig::getConfigName);
        return matterConfigMapper.selectList(q).stream()
                .collect(java.util.stream.Collectors.toMap(
                        MatterProgressDocConfig::getConfigName,
                        c -> MatterConfigOptionDto.builder()
                                .configName(c.getConfigName())
                                .configRole(c.getConfigRole())
                                .presetTypeCode(c.getPresetTypeCode())
                                .enabled(c.getEnabled())
                                .build(),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
    }
}
