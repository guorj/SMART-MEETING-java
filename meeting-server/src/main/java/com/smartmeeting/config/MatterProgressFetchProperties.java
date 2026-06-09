package com.smartmeeting.config;

import com.smartmeeting.matterprogress.config.SpreadsheetFetchLimits;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "matter-progress.fetch")
public class MatterProgressFetchProperties {

    private int maxSheets = 20;
    private int defaultMaxRows = 200;
    private int defaultMaxCols = 26;
    private int absMaxRows = 500;
    private int absMaxCols = 50;

    public SpreadsheetFetchLimits toLimits() {
        return new SpreadsheetFetchLimits(maxSheets, defaultMaxRows, defaultMaxCols, absMaxRows, absMaxCols);
    }
}
