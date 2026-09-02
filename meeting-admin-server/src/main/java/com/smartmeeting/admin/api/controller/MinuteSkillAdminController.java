package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.MinuteSkillBindingDto;
import com.smartmeeting.admin.api.dto.MinuteSkillCatalogItemDto;
import com.smartmeeting.admin.api.dto.UpdateMinuteSkillBindingRequest;
import com.smartmeeting.admin.service.MinuteSkillBindingService;
import com.smartmeeting.admin.service.MinuteSkillCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/minute-skills")
@RequiredArgsConstructor
public class MinuteSkillAdminController {

    private final MinuteSkillCatalogService catalogService;
    private final MinuteSkillBindingService bindingService;

    @GetMapping("/catalog")
    public ApiResponse<List<MinuteSkillCatalogItemDto>> catalog() {
        return ApiResponse.ok(catalogService.listCatalog());
    }

    @GetMapping("/bindings")
    public ApiResponse<List<MinuteSkillBindingDto>> bindings() {
        return ApiResponse.ok(bindingService.listBindings());
    }

    @PutMapping("/bindings/{presetTypeCode}")
    public ApiResponse<MinuteSkillBindingDto> updateBinding(
            @PathVariable int presetTypeCode,
            @RequestBody UpdateMinuteSkillBindingRequest request) {
        return ApiResponse.ok(bindingService.updateBinding(presetTypeCode, request));
    }
}
