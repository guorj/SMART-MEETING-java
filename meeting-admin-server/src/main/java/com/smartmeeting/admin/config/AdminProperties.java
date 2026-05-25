package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.admin")
public class AdminProperties {
    private boolean enabled = true;
    private String token = "";
    private List<String> modules = new ArrayList<>();
}
