package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.agenda.PresetTableAgendaConfigProvider;
import com.smartmeeting.admin.api.dto.HostAgendaBundleItemDto;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.api.dto.MatterConfigOptionDto;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.config.agenda.AgendaConfigProviderRegistry;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaItem;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.config.agenda.PresetAgendaMergeEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgendaConfigService {

    private final AgendaConfigProviderRegistry registry;
    private final PresetTableAgendaConfigProvider presetProvider;
    private final HostAgendaJsonHelper hostAgendaJsonHelper;
    private final MeetingServerBridgeService meetingServerBridge;
    private final ObjectMapper objectMapper;

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
        meetingServerBridge.refreshPresetCache(presetTypeCode);
    }

    private void assertConfigNamesUniqueAcrossPresets(int editingPreset, Set<String> namesInPayload) {
        if (namesInPayload.isEmpty()) {
            return;
        }
        for (int code = 1; code <= 5; code++) {
            if (code == editingPreset) {
                continue;
            }
            int otherCode = code;
            presetProvider.loadPreset(otherCode).ifPresent(p -> {
                for (AgendaDocBindingSnapshot b : PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                        otherCode, p.getHostAgendaJson(), objectMapper)) {
                    if (b.getConfigName() != null && namesInPayload.contains(b.getConfigName().trim())) {
                        throw new BusinessException("config_name 已在 preset " + otherCode + " 使用: " + b.getConfigName());
                    }
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
        for (int code = 1; code <= 5; code++) {
            int finalCode = code;
            presetProvider.loadPreset(code).ifPresent(p -> {
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
}
