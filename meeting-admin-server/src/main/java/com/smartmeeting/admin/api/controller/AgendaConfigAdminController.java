package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.HostAgendaItemRowDto;
import com.smartmeeting.admin.service.AgendaConfigService;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/presets/{code}/doc-bindings")
    public ApiResponse<List<AgendaDocBindingSnapshot>> listBindings(@PathVariable int code) {
        return ApiResponse.ok(agendaConfigService.listDocBindings(code));
    }

    @PostMapping("/presets/{code}/doc-bindings")
    public ApiResponse<Map<String, Long>> createBinding(@PathVariable int code,
                                                        @RequestBody AgendaDocBindingSnapshot body) {
        body.setPresetTypeCode(code);
        long id = agendaConfigService.saveDocBinding(body);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/doc-bindings/{id}")
    public ApiResponse<Void> updateBinding(@PathVariable long id, @RequestBody AgendaDocBindingSnapshot body) {
        body.setId(id);
        agendaConfigService.saveDocBinding(body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/doc-bindings/{id}")
    public ApiResponse<Void> deleteBinding(@PathVariable long id) {
        agendaConfigService.deleteDocBinding(id);
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

    @PostMapping("/presets/{code}/validate-agenda")
    public ApiResponse<List<String>> validateAgenda(@PathVariable int code,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String json = body != null ? body.get("hostAgendaJson") : null;
        if (json == null) {
            json = agendaConfigService.loadBundle(code).getHostAgendaJson();
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
}
