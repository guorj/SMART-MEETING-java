package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.pipeline")
public class MeetingPipelineProperties {

    private boolean preOnCreateEnabled = false;
    private PostAutoTrigger postAutoTrigger = new PostAutoTrigger();

    @Data
    public static class PostAutoTrigger {
        private boolean enabled = false;
        private String templateCode = "";
    }
}
