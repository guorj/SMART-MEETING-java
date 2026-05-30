package com.smartmeeting.config.agenda;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HostAgendaItem {
    private String title;
    private Integer minutes;
    /** 会序负责人（飞书 user_id），可为空或多个 */
    private List<String> owners;
    private String detail;
    private String feishuDocUrl;
    private List<HostAgendaFeishuDocRef> feishuDocs;
    /** host_agenda v2：与会序强绑定的资料列表 */
    private List<HostAgendaDocBinding> docs;
}
