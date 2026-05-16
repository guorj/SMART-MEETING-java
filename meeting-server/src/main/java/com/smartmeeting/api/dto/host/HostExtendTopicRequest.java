package com.smartmeeting.api.dto.host;

import lombok.Data;

@Data
public class HostExtendTopicRequest {
    /** 加时分钟数，仅允许 1、3、5、10；缺省为 1 */
    private Integer minutes;
}
