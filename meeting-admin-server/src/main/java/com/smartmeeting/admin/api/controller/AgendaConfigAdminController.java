package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.HostAgendaBundleItemDto;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.service.AgendaConfigService;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/agenda-config")
@RequiredArgsConstructor
public class AgendaConfigAdminController {

    private final AgendaConfigService agendaConfigService;

    @GetMapping("/providers")
    public ApiResponse<List<String>> providers() {
        return ApiResponse.ok(agendaConfigService.listProviderIds());
    }

    @GetMapping("/presets")
    public ApiResponse<List<AgendaPresetSnapshot>> listPresets() {
        return ApiResponse.ok(agendaConfigService.listPresetHeaders());
    }

    /** 按 config_name 检索其在各 preset host_agenda 中的位置（与保存冲突校验同源） */
    @GetMapping("/config-names/{name}/locations")
    public ApiResponse<List<String>> configNameLocations(@PathVariable String name) {
        return ApiResponse.ok(agendaConfigService.findConfigNameLocations(name));
    }

    @PostMapping("/presets")
    public ApiResponse<Map<String, Integer>> createPreset(@RequestBody(required = false) CreatePresetRequest body) {
        Integer wantedCode = body != null ? body.getPresetTypeCode() : null;
        String name = body != null ? body.getDisplayName() : null;
        int code = agendaConfigService.createPreset(wantedCode, name);
        return ApiResponse.ok(Map.of("code", code));
    }

    @Data
    public static class CreatePresetRequest {
        private Integer presetTypeCode;
        private String displayName;
    }

    @GetMapping("/presets/{code}")
    public ApiResponse<AgendaPresetSnapshot> getPreset(@PathVariable int code) {
        return ApiResponse.ok(agendaConfigService.loadBundle(code));
    }

    @PutMapping("/presets/{code}")
    public ApiResponse<Void> savePreset(@PathVariable int code, @RequestBody AgendaPresetSnapshot body) {
        body.setPresetTypeCode(code);
        agendaConfigService.savePreset(body);
        return ApiResponse.ok();
    }

    /** 仅保存 host_agenda JSON，不修改 display_name 等 meta（与 PUT /presets 区分） */
    @PutMapping("/presets/{code}/host-agenda-json")
    public ApiResponse<Void> saveHostAgendaJson(@PathVariable int code,
                                                @RequestBody Map<String, String> body) {
        String json = body != null ? body.get("hostAgendaJson") : null;
        agendaConfigService.saveHostAgendaJsonOnly(code, json);
        return ApiResponse.ok();
    }

    @PostMapping("/presets/{code}/preview")
    public ApiResponse<List<String>> preview(@PathVariable int code) {
        return ApiResponse.ok(agendaConfigService.previewMergeLines(code));
    }

    @GetMapping("/presets/{code}/agenda-items")
    public ApiResponse<List<HostAgendaItemRowDto>> agendaItems(@PathVariable int code) {
        AgendaPresetSnapshot p = agendaConfigService.loadBundle(code);
        return ApiResponse.ok(agendaConfigService.parseAgendaItems(p.getHostAgendaJson()));
    }

    @PutMapping("/presets/{code}/agenda-items")
    public ApiResponse<Void> saveAgendaItems(@PathVariable int code,
                                             @RequestBody List<HostAgendaItemRowDto> items) {
        agendaConfigService.saveAgendaItems(code, items);
        return ApiResponse.ok();
    }

    @GetMapping("/presets/{code}/agenda-bundle")
    public ApiResponse<List<HostAgendaBundleItemDto>> agendaBundle(@PathVariable int code) {
        return ApiResponse.ok(agendaConfigService.loadAgendaBundle(code));
    }

    @PutMapping("/presets/{code}/agenda-bundle")
    public ApiResponse<Void> saveAgendaBundle(@PathVariable int code,
                                              @RequestBody List<HostAgendaBundleItemDto> items) {
        agendaConfigService.saveAgendaBundle(code, items);
        return ApiResponse.ok();
    }

    @PostMapping("/presets/{code}/validate-agenda")
    public ApiResponse<List<String>> validateAgenda(@PathVariable int code,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String json = body != null ? body.get("hostAgendaJson") : null;
        if (json == null || json.isBlank()) {
            return ApiResponse.ok(agendaConfigService.validateAgendaBundle(code));
        }
        return ApiResponse.ok(agendaConfigService.validateAgenda(json));
    }

    @PostMapping("/presets/{code}/refresh-meetings-host-agenda")
    public ApiResponse<Map<String, Object>> refreshMeetings(
            @PathVariable int code,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ApiResponse.ok(agendaConfigService.refreshUnstartedMeetingsHostAgenda(code, dryRun));
    }

    @PostMapping("/presets/{code}/refresh-cache")
    public ApiResponse<Void> refreshCache(@PathVariable int code) {
        agendaConfigService.refreshPresetCache(code);
        return ApiResponse.ok();
    }

    @PostMapping("/presets/{code}/trigger-owner-confirm-notify")
    public ApiResponse<Map<String, Object>> triggerOwnerConfirmNotify(
            @PathVariable int code,
            @RequestBody TriggerOwnerConfirmNotifyReq body) {
        return ApiResponse.ok(agendaConfigService.triggerOwnerConfirmNotify(
                code,
                body != null ? body.getTemplateCode() : null,
                body == null || body.getSkipExisting() == null || body.getSkipExisting()));
    }

    @PostMapping("/presets/{code}/agenda/{index}/oabp-columns")
    public ApiResponse<Map<String, Object>> oabpColumns(
            @PathVariable int code,
            @PathVariable int index,
            @RequestBody OabpProbeRequest body) {
        return ApiResponse.ok(agendaConfigService.probeOabpColumns(body != null ? body.getOabpTaskSql() : null));
    }

    @PostMapping("/presets/{code}/agenda/{index}/oabp-preview")
    public ApiResponse<Map<String, Object>> oabpPreview(
            @PathVariable int code,
            @PathVariable int index,
            @RequestBody OabpPreviewRequest body) {
        if (body == null) {
            body = new OabpPreviewRequest();
        }
        return ApiResponse.ok(agendaConfigService.previewOabp(
                body.getOabpTaskSql(), body.getOabpDisplayTemplate(), body.getOabpTaskSqlStrict()));
    }

    @GetMapping("/oabp-sql/presets")
    public ApiResponse<List<Map<String, Object>>> oabpSqlPresets() {
        return ApiResponse.ok(agendaConfigService.listOabpSqlPresets());
    }

    @GetMapping("/oabp-display/presets")
    public ApiResponse<List<Map<String, Object>>> oabpDisplayPresets() {
        return ApiResponse.ok(agendaConfigService.listOabpDisplayPresets());
    }

    @GetMapping("/oabp-display/options")
    public ApiResponse<Map<String, Object>> oabpDisplayOptions() {
        return ApiResponse.ok(agendaConfigService.oabpDisplayOptions());
    }

    @Data
    public static class OabpProbeRequest {
        private String oabpTaskSql;
    }

    @Data
    public static class OabpPreviewRequest {
        private String oabpTaskSql;
        private OabpDisplayTemplate oabpDisplayTemplate;
        private Boolean oabpTaskSqlStrict;
    }

    @Data
    public static class TriggerOwnerConfirmNotifyReq {
        private String templateCode;
        private Boolean skipExisting;
    }
}
