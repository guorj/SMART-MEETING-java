package com.smartmeeting.admin.api.dto;

import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会序行 + 该行资料绑定（管理端一体化编辑）。
 */
@Data
@Builder
public class HostAgendaBundleItemDto {
    private int index;
    private String title;
    private Integer minutes;
    @Builder.Default
    private List<String> owners = new ArrayList<>();
    private boolean hasRollCallKeyword;
    /** 加载时存在 agenda_index 越界或未挂载的资料 */
    private boolean hasOrphanDocs;
    @Builder.Default
    private List<AgendaDocBindingSnapshot> bindings = new ArrayList<>();
    /** 可选：oabp 库只读 SELECT，主持页展示为「项目任务」表格 */
    private String oabpTaskSql;
}
