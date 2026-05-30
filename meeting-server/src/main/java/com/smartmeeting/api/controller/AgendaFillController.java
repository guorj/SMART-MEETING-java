package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.service.AgendaFillCampaignService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agenda-fill")
@RequiredArgsConstructor
public class AgendaFillController {

    private final AgendaFillCampaignService agendaFillCampaignService;

    @GetMapping("/session")
    public ApiResponse<AgendaFillCampaignService.SessionView> session(@RequestParam String token) {
        return ApiResponse.ok(agendaFillCampaignService.loadSession(token));
    }

    @PostMapping("/submit")
    public ApiResponse<AgendaFillCampaignService.SubmitResult> submit(@RequestBody SubmitReq req) {
        return ApiResponse.ok(agendaFillCampaignService.submit(req.getToken(), req.getUpdates()));
    }

    @Data
    public static class SubmitReq {
        private String token;
        private List<AgendaFillCampaignService.AgendaFillUpdate> updates;
    }
}

