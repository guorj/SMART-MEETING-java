package com.smartmeeting.pipeline.executor;

import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class WeeklyJobStepExecutor implements StepExecutor {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${meeting.notification.bot-base-url:http://127.0.0.1:8764}")
    private String botBaseUrl;
    @Value("${meeting.notification.scheduled-bot-apikey:jq_int_meeting_key}")
    private String apiKey;

    @Override
    public String stepType() {
        return "weekly-job";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        try {
            String url = botBaseUrl.replaceAll("/$", "") + "/api/admin/reload-schedule";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>("{}", headers), String.class);
            return StepExecutionResult.ok("weekly-job-http-" + resp.getStatusCode().value(), context.getStep().getConfigJson());
        } catch (Exception e) {
            log.warn("weekly-job executor failed: {}", e.getMessage());
            return StepExecutionResult.failed(e.getMessage());
        }
    }
}
