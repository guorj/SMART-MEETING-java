package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.SystemConfigSchemaItemDto;
import com.smartmeeting.admin.service.SystemConfigAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/system-config")
@RequiredArgsConstructor
public class SystemConfigAdminController {

    private final SystemConfigAdminService systemConfigAdminService;

    @GetMapping("/schema")
    public ApiResponse<List<SystemConfigSchemaItemDto>> schema() {
        return ApiResponse.ok(systemConfigAdminService.schema());
    }

    @PutMapping("/entries/{key}")
    public ApiResponse<Map<String, Object>> upsert(@PathVariable String key,
                                                   @RequestBody Map<String, String> body) {
        String value = body.get("valueJson");
        systemConfigAdminService.upsert(key, value);
        boolean reloaded = systemConfigAdminService.triggerRuntimeReload();
        return ApiResponse.ok(Map.of("reloaded", reloaded));
    }

    @DeleteMapping("/entries/{key}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String key) {
        systemConfigAdminService.delete(key);
        boolean reloaded = systemConfigAdminService.triggerRuntimeReload();
        return ApiResponse.ok(Map.of("reloaded", reloaded));
    }

    @PostMapping("/reload-runtime")
    public ApiResponse<Map<String, Object>> reload() {
        return ApiResponse.ok(Map.of("reloaded", systemConfigAdminService.triggerRuntimeReload()));
    }

    @GetMapping("/audit")
    public ApiResponse<java.util.List<com.smartmeeting.admin.api.dto.SystemConfigAuditDto>> audit(
            @RequestParam(required = false) String configKey,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(systemConfigAdminService.listAudit(configKey, limit));
    }
}
