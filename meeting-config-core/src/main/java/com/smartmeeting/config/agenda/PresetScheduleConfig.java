package com.smartmeeting.config.agenda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会务类型预设的机器可读排期规则，存于 {@code int_meeting_type_preset.schedule_config}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetScheduleConfig {
    /** weekly | fixed | at_start */
    private String type;
    /** 1=周一 … 7=周日（weekly） */
    private Integer weekday;
    private Integer hour;
    private Integer minute;
    /** fixed 单次时刻 */
    private LocalDateTime at;
    /** weekly：本周场次已过时是否取下一周（默认 true） */
    private Boolean preferNextIfPast;
}
