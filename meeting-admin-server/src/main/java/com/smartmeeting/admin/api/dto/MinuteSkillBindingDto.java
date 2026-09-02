package com.smartmeeting.admin.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinuteSkillBindingDto {
    private Integer presetTypeCode;
    private String displayName;
    private String minuteSkillName;
}
