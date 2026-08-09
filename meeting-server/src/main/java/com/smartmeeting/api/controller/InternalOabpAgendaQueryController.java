package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.config.datasource.OabpDataSourceProperties;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import com.smartmeeting.service.oabp.OabpAgendaPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/oabp/agenda-query")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class InternalOabpAgendaQueryController {

    private final InternalApiAuth internalApiAuth;
    private final OabpAgendaPreviewService previewService;
    private final OabpDataSourceProperties oabpProperties;

    @PostMapping("/columns")
    public ApiResponse<Map<String, Object>> columns(HttpServletRequest request, @RequestBody OabpSqlRequest body) {
        internalApiAuth.requireToken(request);
        return ApiResponse.ok(previewService.probeColumns(body.getOabpTaskSql()));
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(HttpServletRequest request, @RequestBody OabpPreviewRequest body) {
        internalApiAuth.requireToken(request);
        return ApiResponse.ok(previewService.preview(
                body.getOabpTaskSql(),
                body.getOabpDisplayTemplate(),
                oabpProperties.getMaxQueryRows(),
                Boolean.TRUE.equals(body.getOabpTaskSqlStrict())));
    }

    @Data
    public static class OabpSqlRequest {
        private String oabpTaskSql;
    }

    @Data
    public static class OabpPreviewRequest {
        private String oabpTaskSql;
        private OabpDisplayTemplate oabpDisplayTemplate;
        private Boolean oabpTaskSqlStrict;
    }
}
