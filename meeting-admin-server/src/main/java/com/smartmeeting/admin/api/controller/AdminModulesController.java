package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.module.AdminModuleRegistry;
import com.smartmeeting.config.admin.AdminModuleManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminModulesController {

    private final AdminModuleRegistry registry;

    @GetMapping("/modules")
    public ApiResponse<List<AdminModuleManifest>> modules() {
        return ApiResponse.ok(registry.manifests());
    }
}
