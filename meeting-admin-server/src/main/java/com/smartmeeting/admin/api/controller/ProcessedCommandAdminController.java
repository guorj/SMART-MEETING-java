package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.entity.ProcessedCommand;
import com.smartmeeting.admin.service.ProcessedCommandAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/processed-commands")
@RequiredArgsConstructor
public class ProcessedCommandAdminController {

    private final ProcessedCommandAdminService processedCommandAdminService;

    @GetMapping
    public ApiResponse<Page<ProcessedCommand>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String commandType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String commandKey) {
        return ApiResponse.ok(processedCommandAdminService.list(page, size, commandType, aggregateType, aggregateId, commandKey));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(processedCommandAdminService.stats());
    }
}
