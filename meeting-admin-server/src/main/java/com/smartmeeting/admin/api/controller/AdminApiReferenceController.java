package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.AdminApiReferenceEntryDto;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.AdminDataApiCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/api-reference")
@RequiredArgsConstructor
public class AdminApiReferenceController {

    private final AdminDataApiCatalogService catalogService;

    @GetMapping("/catalog")
    public ApiResponse<List<AdminApiReferenceEntryDto>> catalog() {
        return ApiResponse.ok(catalogService.buildCatalog());
    }

    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> meta() {
        List<AdminApiReferenceEntryDto> all = catalogService.buildCatalog();
        Map<String, Long> byCategory = all.stream()
                .collect(Collectors.groupingBy(AdminApiReferenceEntryDto::getCategory, Collectors.counting()));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("total", all.size());
        meta.put("categories", byCategory);
        meta.put("adminBaseUrl", "http://127.0.0.1:8766");
        meta.put("meetingServerBaseUrl", "http://127.0.0.1:8765/meeting-server");
        meta.put("authHint", "管理端请求头 X-Admin-Token；meeting-server internal 使用 X-Internal-Token");
        meta.put("responseWrapper", "{ \"code\": 0, \"message\": \"ok\", \"data\": ... }");
        return ApiResponse.ok(meta);
    }
}
