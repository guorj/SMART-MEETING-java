package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.agenda.PresetTableAgendaConfigProvider;
import com.smartmeeting.admin.api.dto.HostAgendaBundleItemDto;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.api.dto.MatterConfigOptionDto;
import com.smartmeeting.admin.entity.MeetingTypePreset;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingTypePresetMapper;
import com.smartmeeting.config.agenda.AgendaConfigProviderRegistry;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaConfigService {

    private final AgendaConfigProviderRegistry registry;
    private final PresetTableAgendaConfigProvider presetProvider;
    private final HostAgendaJsonHelper hostAgendaJsonHelper;
    private final MeetingServerBridgeService meetingServerBridge;
    private final MeetingTypePresetMapper presetMapper;
    private final ObjectMapper objectMapper;

    public List<AgendaPresetSnapshot> listPresetHeaders() {
        List<MeetingTypePreset> rows = presetMapper.selectList(null);
        rows.sort((a, b) -> Integer.compare(a.getCode(), b.getCode()));
        List<AgendaPresetSnapshot> out = new ArrayList<>();
        for (MeetingTypePreset row : rows) {
            out.add(AgendaPresetSnapshot.builder()
                    .presetTypeCode(row.getCode())
                    .displayName(row.getDisplayName())
                    .company(row.getCompany())
                    .groupName(row.getGroupName())
                    .build());
        }
        return out;
    }

    public int createPreset(Integer wantedCode, String displayName) {
        int code = wantedCode != null ? wantedCode : nextPresetCode();
        if (code <= 0) {
            throw new BusinessException("preset code 必须为正整数");
        }
        if (code > 127) {
            throw new BusinessException("preset code 超出上限（当前库表为 TINYINT，最大 127）");
        }
        if (presetMapper.selectById(code) != null) {
            throw new BusinessException("preset code 已存在: " + code);
        }
        AgendaPresetSnapshot snap = AgendaPresetSnapshot.builder()
                .presetTypeCode(code)
                .displayName(displayName != null && !displayName.isBlank() ? displayName.trim() : ("会务类型" + code))
                .company("未设置集团")
                .groupName("未设置会议组")
                .department("")
                .scheduleNote("")
                .agendaSummary("")
                .organizerName("")
                .leaderName("")
                .participantsNames("")
                .hostAgendaJson("{\"version\":2,\"items\":[]}")
                .build();
        savePreset(snap);
        return code;
    }

    public List<String> listProviderIds() {
        return registry.providerIds();
    }

    public AgendaPresetSnapshot loadBundle(int presetTypeCode) {
        AgendaPresetSnapshot preset = presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found: " + presetTypeCode));
        preset.setDocBindings(PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                presetTypeCode, preset.getHostAgendaJson(), objectMapper));
        return preset;
    }

    public void savePreset(AgendaPresetSnapshot snapshot) {
        if (snapshot.getHostAgendaJson() != null && !snapshot.getHostAgendaJson().isBlank()) {
            hostAgendaJsonHelper.parseItems(snapshot.getHostAgendaJson());
        }
        presetProvider.savePreset(snapshot);
        refreshPresetCacheBestEffort(snapshot.getPresetTypeCode());
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

    public List<HostAgendaBundleItemDto> loadAgendaBundle(int presetTypeCode) {
        AgendaPresetSnapshot preset = presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found: " + presetTypeCode));
        List<HostAgendaItem> items = HostAgendaJsonCodec.parseItems(objectMapper, preset.getHostAgendaJson());
        List<HostAgendaBundleItemDto> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            HostAgendaItem item = items.get(i);
            List<AgendaDocBindingSnapshot> bindings = HostAgendaJsonCodec.extractBindingsForAgenda(
                    presetTypeCode, preset.getHostAgendaJson(), i, objectMapper);
            result.add(HostAgendaBundleItemDto.builder()
                    .index(i)
                    .title(item.getTitle())
                    .minutes(item.getMinutes())
                    .owners(item.getOwners() != null ? new ArrayList<>(item.getOwners()) : new ArrayList<>())
                    .hasRollCallKeyword(item.getTitle() != null && item.getTitle().contains("检点"))
                    .bindings(bindings)
                    .build());
        }
        return result;
    }

    public void saveAgendaBundle(int presetTypeCode, List<HostAgendaBundleItemDto> items) {
        if (items == null) {
            items = List.of();
        }
        Set<String> configNames = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            HostAgendaBundleItemDto item = items.get(i);
            if (item.getBindings() == null) {
                continue;
            }
            Set<Integer> slots = new HashSet<>();
            for (AgendaDocBindingSnapshot b : item.getBindings()) {
                int slot = b.getResourceSlot() != null ? b.getResourceSlot() : 0;
                if (!slots.add(slot)) {
                    throw new BusinessException(
                            "会序 " + (i + 1) + " 存在重复 resource_slot=" + slot);
                }
                if (b.getConfigName() != null && !b.getConfigName().isBlank()) {
                    String name = b.getConfigName().trim();
                    if (!configNames.add(name)) {
                        throw new BusinessException("config_name 重复: " + name);
                    }
                }
            }
        }
        assertConfigNamesUniqueAcrossPresets(presetTypeCode, configNames);

        List<HostAgendaItem> coreItems = new ArrayList<>();
        for (HostAgendaBundleItemDto item : items) {
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                continue;
            }
            HostAgendaItem hi = new HostAgendaItem();
            hi.setTitle(item.getTitle().trim());
            hi.setMinutes(item.getMinutes() != null && item.getMinutes() > 0 ? item.getMinutes() : 10);
            if (item.getOwners() != null && !item.getOwners().isEmpty()) {
                List<String> owners = item.getOwners().stream()
                        .filter(v -> v != null && !v.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
                hi.setOwners(owners);
            } else {
                hi.setOwners(List.of());
            }
            List<HostAgendaDocBinding> docs = new ArrayList<>();
            if (item.getBindings() != null) {
                for (AgendaDocBindingSnapshot b : item.getBindings()) {
                    b.setPresetTypeCode(presetTypeCode);
                    if (b.getEnabled() == null) {
                        b.setEnabled(1);
                    }
                    HostAgendaDocBinding doc = HostAgendaJsonCodec.fromSnapshot(b);
                    if (doc != null) {
                        docs.add(doc);
                    }
                }
            }
            hi.setDocs(docs);
            coreItems.add(hi);
        }
        AgendaPresetSnapshot snap = presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found"));
        snap.setHostAgendaJson(HostAgendaJsonCodec.toJson(objectMapper, coreItems));
        presetProvider.savePreset(snap);
        refreshPresetCacheBestEffort(presetTypeCode);
    }

    private void refreshPresetCacheBestEffort(int presetTypeCode) {
        try {
            meetingServerBridge.refreshPresetCache(presetTypeCode);
        } catch (Exception e) {
            // 兼容联调期：meeting-server 可能尚未暴露 refresh-cache internal 接口，不应阻塞预设保存。
            log.warn("refresh preset cache skipped after save, presetTypeCode={}, reason={}",
                    presetTypeCode, e.getMessage());
        }
    }

    /**
     * 全局检索 config_name 在哪些会务类型的 host_agenda 中出现（与保存校验同源）。
     */
    public List<String> findConfigNameLocations(String configName) {
        if (configName == null || configName.isBlank()) {
            return List.of();
        }
        String needle = configName.trim();
        List<String> hits = new ArrayList<>();
        for (Integer code : listPresetCodes()) {
            if (code == null) {
                continue;
            }
            presetProvider.loadPreset(code).ifPresent(p -> hits.addAll(
                    describeConfigNameHits(code, p.getDisplayName(), p.getHostAgendaJson(), needle)));
        }
        return hits;
    }

    private List<String> describeConfigNameHits(int presetTypeCode, String displayName,
                                                String hostAgendaJson, String needle) {
        List<String> hits = new ArrayList<>();
        String label = displayName != null && !displayName.isBlank()
                ? displayName.trim() : ("会务类型" + presetTypeCode);
        for (AgendaDocBindingSnapshot b : PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                presetTypeCode, hostAgendaJson, objectMapper)) {
            if (b.getConfigName() == null || !needle.equals(b.getConfigName().trim())) {
                continue;
            }
            int agenda = b.getAgendaIndex() != null ? b.getAgendaIndex() + 1 : 0;
            String url = b.getFeishuDocUrl() != null ? b.getFeishuDocUrl().trim() : "";
            if (url.length() > 72) {
                url = url.substring(0, 72) + "…";
            }
            hits.add(String.format(
                    "会务类型 %d（%s）· 会序 %d · 槽位 %d · 角色 %s · url=%s",
                    presetTypeCode, label, agenda,
                    b.getResourceSlot() != null ? b.getResourceSlot() : 0,
                    b.getConfigRole() != null ? b.getConfigRole() : "SOURCE",
                    url.isEmpty() ? "—" : url));
        }
        return hits;
    }

    private void assertConfigNamesUniqueAcrossPresets(int editingPreset, Set<String> namesInPayload) {
        if (namesInPayload.isEmpty()) {
            return;
        }
        for (Integer code : listPresetCodes()) {
            if (code == null || code == editingPreset) {
                continue;
            }
            presetProvider.loadPreset(code).ifPresent(p -> {
                for (AgendaDocBindingSnapshot b : PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                        code, p.getHostAgendaJson(), objectMapper)) {
                    if (b.getConfigName() == null || !namesInPayload.contains(b.getConfigName().trim())) {
                        continue;
                    }
                    int agenda = b.getAgendaIndex() != null ? b.getAgendaIndex() + 1 : 0;
                    String display = p.getDisplayName() != null && !p.getDisplayName().isBlank()
                            ? p.getDisplayName().trim() : ("会务类型" + code);
                    throw new BusinessException(String.format(
                            "config_name「%s」与会务类型 %d（%s）会序 %d 的资料冲突；"
                                    + "请修改当前资料的配置名，或到会务类型 %d 改名/删除该资料",
                            b.getConfigName().trim(), code, display, agenda, code));
                }
            });
        }
    }

    public Map<String, Object> refreshUnstartedMeetingsHostAgenda(int presetTypeCode, boolean dryRun) {
        presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found"));
        return meetingServerBridge.refreshHostAgendaEnriched(presetTypeCode, dryRun, null);
    }

    public void refreshPresetCache(int presetTypeCode) {
        meetingServerBridge.refreshPresetCache(presetTypeCode);
    }

    public Map<String, Object> triggerOwnerConfirmNotify(int presetTypeCode, String templateCode, boolean skipExisting) {
        presetProvider.loadPreset(presetTypeCode)
                .orElseThrow(() -> new BusinessException("preset not found: " + presetTypeCode));
        String finalTemplate = (templateCode == null || templateCode.isBlank())
                ? "pre_10m_default"
                : templateCode.trim();
        Map<String, Object> out = meetingServerBridge.executePipelineByPreset(
                presetTypeCode, "PRE", finalTemplate, skipExisting);
        out.put("message", "已按 preset 触发会序确认通知");
        return out;
    }

    public List<String> previewMergeLines(int presetTypeCode) {
        AgendaPresetSnapshot bundle = loadBundle(presetTypeCode);
        List<String> lines = new ArrayList<>();
        lines.add("preset=" + bundle.getDisplayName() + " code=" + presetTypeCode);
        if (bundle.getHostAgendaJson() != null && !bundle.getHostAgendaJson().isBlank()) {
            lines.add("host_agenda: " + bundle.getHostAgendaJson());
        }
        if (bundle.getDocBindings() != null) {
            for (AgendaDocBindingSnapshot d : bundle.getDocBindings()) {
                lines.add(String.format("doc idx=%s slot=%s role=%s name=%s url=%s",
                        d.getAgendaIndex(), d.getResourceSlot(), d.getConfigRole(),
                        d.getConfigName(), d.getFeishuDocUrl()));
            }
        }
        return lines;
    }

    public List<MatterConfigOptionDto> listMatterConfigOptions(String role) {
        Map<String, MatterConfigOptionDto> byName = new LinkedHashMap<>();
        for (Integer code : listPresetCodes()) {
            if (code == null) {
                continue;
            }
            int finalCode = code;
            presetProvider.loadPreset(finalCode).ifPresent(p -> {
                for (AgendaDocBindingSnapshot b : PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                        finalCode, p.getHostAgendaJson(), objectMapper)) {
                    if (b.getEnabled() == null || b.getEnabled() != 1) {
                        continue;
                    }
                    String r = b.getConfigRole() != null ? b.getConfigRole().trim().toUpperCase() : "SOURCE";
                    if (role != null && !role.isBlank()) {
                        String want = role.toUpperCase();
                        if ("SOURCE".equals(want) && !("SOURCE".equals(r) || "BOTH".equals(r))) {
                            continue;
                        }
                        if ("OUTPUT".equals(want) && !("OUTPUT".equals(r) || "BOTH".equals(r))) {
                            continue;
                        }
                    }
                    if (b.getConfigName() != null && !b.getConfigName().isBlank()) {
                        byName.putIfAbsent(b.getConfigName(), MatterConfigOptionDto.builder()
                                .configName(b.getConfigName())
                                .configRole(b.getConfigRole())
                                .presetTypeCode(finalCode)
                                .enabled(b.getEnabled())
                                .build());
                    }
                }
            });
        }
        return new ArrayList<>(byName.values());
    }

    private List<Integer> listPresetCodes() {
        List<MeetingTypePreset> rows = presetMapper.selectList(null);
        List<Integer> codes = new ArrayList<>();
        for (MeetingTypePreset row : rows) {
            if (row.getCode() != null && row.getCode() > 0) {
                codes.add(row.getCode());
            }
        }
        codes.sort(Integer::compareTo);
        return codes;
    }

    private int nextPresetCode() {
        int max = 0;
        for (Integer c : listPresetCodes()) {
            if (c != null && c > max) {
                max = c;
            }
        }
        return max + 1;
    }
}
