package com.smartmeeting.config.agenda;

import com.smartmeeting.config.oabp.OabpDisplayTemplate;
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
    /** 可选：oabp 库只读 SQL，查询 jq_project_task_tracking 等表，结果作为会序资料展示 */
    private String oabpTaskSql;
    /**
     * 是否在主持页展示 oabp 项目任务表格；默认 true。
     * 为 false 时保留 SQL（如 weekly-comparison 仍可用），主持页不渲染该 part。
     */
    private Boolean oabpTaskShow;
    /** 可选：oabp 结果集展示模板；为空时直通 raw sheet（向后兼容） */
    private OabpDisplayTemplate oabpDisplayTemplate;
    /** 简洁模式 SQL 预设 id；custom 或 null 表示手写 SQL */
    private String oabpSqlPresetId;
    /**
     * true：主持页与预览严格按 SQL 结果展示（行序、列值），不应用展示模板，不做状态分组/徽章等额外渲染。
     */
    private Boolean oabpTaskSqlStrict;
}
