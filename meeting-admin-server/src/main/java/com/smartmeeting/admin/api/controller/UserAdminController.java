package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.UserMappingDto;
import com.smartmeeting.admin.api.dto.UserProfileDetailDto;
import com.smartmeeting.admin.api.dto.UserProfileSaveDto;
import com.smartmeeting.admin.api.dto.UserProfileSummaryDto;
import com.smartmeeting.admin.api.dto.VoiceprintDto;
import com.smartmeeting.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    /** 用户档案列表（映射 + 主声纹，一行展示完整用户信息） */
    @GetMapping
    public ApiResponse<Page<UserProfileSummaryDto>> listProfiles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "ALL") String expiryFilter) {
        return ApiResponse.ok(userAdminService.listProfiles(page, size, keyword, expiryFilter));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfileDetailDto> getProfile(@PathVariable int userId) {
        return ApiResponse.ok(userAdminService.getProfile(userId));
    }

    @PostMapping
    public ApiResponse<Map<String, Integer>> createProfile(@RequestBody UserProfileSaveDto body) {
        return ApiResponse.ok(Map.of("userId", userAdminService.createProfile(body)));
    }

    @PutMapping("/{userId}")
    public ApiResponse<Void> updateProfile(@PathVariable int userId, @RequestBody UserProfileSaveDto body) {
        userAdminService.updateProfile(userId, body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteProfile(@PathVariable int userId) {
        userAdminService.deleteProfile(userId);
        return ApiResponse.ok();
    }

    @GetMapping("/mappings")
    public ApiResponse<Page<UserMappingDto>> listMappings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userAdminService.listMappings(page, size, keyword));
    }

    @GetMapping("/mappings/{userId}")
    public ApiResponse<UserMappingDto> getMapping(@PathVariable int userId) {
        return ApiResponse.ok(userAdminService.getMapping(userId));
    }

    @PostMapping("/mappings")
    public ApiResponse<Map<String, Integer>> createMapping(@RequestBody UserMappingDto body) {
        return ApiResponse.ok(Map.of("userId", userAdminService.createMapping(body)));
    }

    @PutMapping("/mappings/{userId}")
    public ApiResponse<Void> updateMapping(@PathVariable int userId, @RequestBody UserMappingDto body) {
        userAdminService.updateMapping(userId, body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/mappings/{userId}")
    public ApiResponse<Void> deleteMapping(@PathVariable int userId) {
        userAdminService.deleteMapping(userId);
        return ApiResponse.ok();
    }

    @GetMapping("/voiceprints")
    public ApiResponse<Page<VoiceprintDto>> listVoiceprints(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "ALL") String expiryFilter) {
        return ApiResponse.ok(userAdminService.listVoiceprints(page, size, keyword, expiryFilter));
    }

    @GetMapping("/voiceprints/{id}")
    public ApiResponse<VoiceprintDto> getVoiceprint(@PathVariable String id) {
        return ApiResponse.ok(userAdminService.getVoiceprint(id));
    }

    @PostMapping("/voiceprints")
    public ApiResponse<Map<String, String>> createVoiceprint(@RequestBody VoiceprintDto body) {
        return ApiResponse.ok(Map.of("id", userAdminService.createVoiceprint(body)));
    }

    @PutMapping("/voiceprints/{id}")
    public ApiResponse<Void> updateVoiceprint(@PathVariable String id, @RequestBody VoiceprintDto body) {
        userAdminService.updateVoiceprint(id, body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/voiceprints/{id}")
    public ApiResponse<Void> deleteVoiceprint(@PathVariable String id) {
        userAdminService.deleteVoiceprint(id);
        return ApiResponse.ok();
    }
}
