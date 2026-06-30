package com.smartmeeting.matterprogress.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.ReportBinding;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 从 {@code int_meeting_type_preset.host_agenda} v2 JSON 读写资料配置（替代 doc_config 表）。
 */
public class JdbcPresetHostAgendaConfigRepository implements MatterProgressConfigRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String defaultOabpSchema;

    public JdbcPresetHostAgendaConfigRepository(JdbcTemplate jdbc) {
        this(jdbc, MatterProgressConfigRow.DEFAULT_OABP_SCHEMA);
    }

    public JdbcPresetHostAgendaConfigRepository(JdbcTemplate jdbc, String defaultOabpSchema) {
        this.jdbc = jdbc;
        this.defaultOabpSchema = defaultOabpSchema != null && !defaultOabpSchema.isBlank()
                ? defaultOabpSchema.trim()
                : MatterProgressConfigRow.DEFAULT_OABP_SCHEMA;
    }

    @Override
    public Optional<MatterProgressConfigRow> findByConfigName(String configName) {
        if (configName == null || configName.isBlank()) {
            return Optional.empty();
        }
        String name = configName.trim();
        for (int code = 1; code <= 5; code++) {
            String json = loadHostAgenda(code);
            Optional<HostAgendaJsonCodec.DocContext> located =
                    HostAgendaJsonCodec.findDocContextByConfigName(json, name, mapper);
            if (located.isPresent()) {
                return Optional.of(toRow(code, located.get()));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<MatterProgressConfigRow> loadSources(List<String> configNames) {
        if (configNames == null || configNames.isEmpty()) {
            return List.of();
        }
        List<MatterProgressConfigRow> out = new ArrayList<>();
        for (String n : configNames) {
            findByConfigName(n).ifPresent(row -> {
                if (!row.enabled()) {
                    return;
                }
                String role = row.configRole() != null ? row.configRole().trim().toUpperCase(Locale.ROOT) : "";
                if (("SOURCE".equals(role) || "BOTH".equals(role)) && row.hasOabpTaskSql()) {
                    out.add(row);
                }
            });
        }
        return out;
    }

    @Override
    public void writeGeneratedReport(String outputConfigName, String reportUrl, Instant generatedAt) {
        if (outputConfigName == null || outputConfigName.isBlank()) {
            throw new IllegalArgumentException("outputConfigName 为空");
        }
        String name = outputConfigName.trim();
        LocalDateTime at = generatedAt != null
                ? LocalDateTime.ofInstant(generatedAt, ZoneId.systemDefault())
                : LocalDateTime.now();
        for (int code = 1; code <= 5; code++) {
            String json = loadHostAgenda(code);
            if (HostAgendaJsonCodec.findDocByConfigName(json, name, mapper).isEmpty()) {
                continue;
            }
            String updated = HostAgendaJsonCodec.updateGeneratedReport(mapper, json, name, reportUrl, at);
            int n = jdbc.update(
                    "UPDATE int_meeting_type_preset SET host_agenda = CAST(? AS JSON) WHERE code = ?",
                    updated,
                    code);
            if (n > 0) {
                return;
            }
        }
        throw new IllegalStateException("写回失败，未在 preset host_agenda 中找到 config_name=" + name);
    }

    @Override
    public void writeGeneratedReportRun(String outputConfigName, long runId, Instant generatedAt) {
        if (outputConfigName == null || outputConfigName.isBlank()) {
            throw new IllegalArgumentException("outputConfigName 为空");
        }
        String name = outputConfigName.trim();
        LocalDateTime at = generatedAt != null
                ? LocalDateTime.ofInstant(generatedAt, ZoneId.systemDefault())
                : LocalDateTime.now();
        for (int code = 1; code <= 5; code++) {
            String json = loadHostAgenda(code);
            if (HostAgendaJsonCodec.findDocByConfigName(json, name, mapper).isEmpty()) {
                continue;
            }
            String updated = HostAgendaJsonCodec.updateGeneratedReportRun(mapper, json, name, runId, at);
            int n = jdbc.update(
                    "UPDATE int_meeting_type_preset SET host_agenda = CAST(? AS JSON) WHERE code = ?",
                    updated,
                    code);
            if (n > 0) {
                return;
            }
        }
        throw new IllegalStateException("写回 run id 失败，未在 preset host_agenda 中找到 config_name=" + name);
    }

    @Override
    public Optional<ReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex) {
        if (presetTypeCode < 1 || presetTypeCode > 5 || agendaIndex < 0) {
            return Optional.empty();
        }
        String json = loadHostAgenda(presetTypeCode);
        var item = HostAgendaJsonCodec.parseItemAtIndex(mapper, json, agendaIndex);
        if (item == null || item.getDocs() == null) {
            return Optional.empty();
        }
        HostAgendaDocBinding row = item.getDocs().stream()
                .filter(d -> d != null && d.isEnabled())
                .filter(d -> {
                    String role = d.getRole() != null ? d.getRole().trim().toUpperCase(Locale.ROOT) : "";
                    return "OUTPUT".equals(role) || "BOTH".equals(role);
                })
                .max(java.util.Comparator.comparingInt(HostAgendaDocBinding::resolvedSlot))
                .orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        // 阻断 1 修正：runId 或 url 任一非空即返回（双兼容）
        boolean hasRunId = row.getGeneratedReportRunId() != null;
        boolean hasUrl = row.getGeneratedReportUrl() != null && !row.getGeneratedReportUrl().isBlank();
        if (!hasRunId && !hasUrl) {
            return Optional.empty();
        }
        String outputFeishu = null;
        if ("OUTPUT".equalsIgnoreCase(row.getRole() != null ? row.getRole().trim() : "")
                && row.getUrl() != null && !row.getUrl().isBlank()) {
            outputFeishu = row.getUrl().trim();
        }
        Instant genAt = row.getGeneratedReportAt() != null
                ? row.getGeneratedReportAt().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        return Optional.of(new ReportBinding(
                row.getConfigName(),
                row.getGeneratedReportUrl(),
                genAt,
                row.getGeneratedReportRunId(),
                outputFeishu));
    }

    private String loadHostAgenda(int code) {
        List<String> rows = jdbc.query(
                "SELECT host_agenda FROM int_meeting_type_preset WHERE code = ?",
                (rs, rowNum) -> rs.getString("host_agenda"),
                code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MatterProgressConfigRow toRow(int presetCode, HostAgendaJsonCodec.DocContext located) {
        HostAgendaDocBinding doc = located.doc();
        long syntheticId = presetCode * 10_000L + located.agendaIndex() * 100L + doc.resolvedSlot();
        Instant genAt = doc.getGeneratedReportAt() != null
                ? doc.getGeneratedReportAt().atZone(ZoneId.systemDefault()).toInstant()
                : null;
        String oabpSql = located.oabpTaskSql() != null && !located.oabpTaskSql().isBlank()
                ? located.oabpTaskSql().strip()
                : null;
        return new MatterProgressConfigRow(
                syntheticId,
                doc.getConfigName(),
                presetCode,
                located.agendaIndex(),
                doc.getRole() != null ? doc.getRole() : "SOURCE",
                doc.getUrl(),
                doc.getGeneratedReportUrl(),
                genAt,
                doc.isEnabled(),
                oabpSql,
                defaultOabpSchema,
                doc.getGeneratedReportRunId());
    }
}
