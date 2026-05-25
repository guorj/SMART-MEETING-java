package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.WeeklyJobDto;
import com.smartmeeting.admin.api.dto.MatterConfigOptionDto;
import com.smartmeeting.admin.service.AgendaConfigService;
import com.smartmeeting.admin.service.WeeklyJobAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/weekly-jobs")
@RequiredArgsConstructor
public class WeeklyJobAdminController {

    private final WeeklyJobAdminService weeklyJobAdminService;
    private final AgendaConfigService agendaConfigService;

    @GetMapping("/matter-config-options")
    public ApiResponse<List<MatterConfigOptionDto>> matterOptions(
            @RequestParam(required = false) String role) {
        return ApiResponse.ok(agendaConfigService.listMatterConfigOptions(role));
    }

    @GetMapping
    public ApiResponse<List<WeeklyJobDto>> list() {
        return ApiResponse.ok(weeklyJobAdminService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<WeeklyJobDto> get(@PathVariable long id) {
        return ApiResponse.ok(weeklyJobAdminService.get(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> create(@RequestBody WeeklyJobDto body) {
        return ApiResponse.ok(Map.of("id", weeklyJobAdminService.create(body)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody WeeklyJobDto body) {
        weeklyJobAdminService.update(id, body);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        weeklyJobAdminService.delete(id);
        return ApiResponse.ok();
    }
}
