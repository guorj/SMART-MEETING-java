package com.smartmeeting.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshots;
import com.smartmeeting.config.agenda.HostAgendaJsonCodec;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v0.14 数据迁移：将 {@code int_matter_progress_doc_config} 行合并进 preset {@code host_agenda} v2。
 * <pre>
 *   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.HostAgendaV2DataMigrate -Dexec.classpathScope=test
 *   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.HostAgendaV2DataMigrate -Dexec.classpathScope=test -Dexec.args="--apply"
 * </pre>
 */
public final class HostAgendaV2DataMigrate {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        boolean apply = args != null && List.of(args).contains("--apply");
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(ds.get("url"), ds.get("username"), ds.get("password"))) {
            int updated = migrate(conn, apply);
            System.out.println((apply ? "Applied" : "Dry-run") + ": would update " + updated + " preset row(s)");
            if (!apply) {
                System.out.println("Pass --apply to write host_agenda v2 JSON.");
            }
        }
    }

    static int migrate(Connection conn, boolean apply) throws SQLException {
        int updated = 0;
        for (int code = 1; code <= 5; code++) {
            String hostAgenda = loadHostAgenda(conn, code);
            if (HostAgendaJsonCodec.isVersion2(hostAgenda, MAPPER)) {
                System.out.println("preset " + code + ": already version 2, skip");
                continue;
            }
            List<AgendaDocBindingSnapshot> docRows = loadDocRows(conn, code);
            String merged = HostAgendaJsonCodec.mergeDocTableRows(MAPPER, hostAgenda, code, docRows);
            if (merged == null || merged.equals(hostAgenda)) {
                if (docRows.isEmpty()) {
                    System.out.println("preset " + code + ": no doc rows and no host_agenda change");
                } else {
                    System.out.println("preset " + code + ": merge produced no change");
                }
                continue;
            }
            System.out.println("preset " + code + ": merge " + docRows.size() + " doc row(s) -> v2 JSON");
            if (apply) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE int_meeting_type_preset SET host_agenda = CAST(? AS JSON) WHERE code = ?")) {
                    ps.setString(1, merged);
                    ps.setInt(2, code);
                    ps.executeUpdate();
                }
            }
            updated++;
        }
        migrateLegacyDefault(conn, apply);
        return updated;
    }

    private static void migrateLegacyDefault(Connection conn, boolean apply) throws SQLException {
        List<AgendaDocBindingSnapshot> legacy;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT config_name, resource_slot, feishu_doc_url, enabled, config_role,
                            bitable_display_mode, generated_report_url, generated_report_at
                     FROM int_matter_progress_doc_config
                     WHERE preset_type_code IS NULL AND config_name = 'default'
                     LIMIT 1
                     """)) {
            if (!rs.next()) {
                return;
            }
            legacy = List.of(AgendaDocBindingSnapshots.of(
                    rs.getLong("id"),
                    rs.getString("config_name"),
                    1,
                    0,
                    rs.getInt("resource_slot"),
                    rs.getString("feishu_doc_url"),
                    rs.getInt("enabled"),
                    rs.getString("config_role"),
                    rs.getString("bitable_display_mode"),
                    rs.getString("generated_report_url"),
                    rs.getTimestamp("generated_report_at") != null
                            ? rs.getTimestamp("generated_report_at").toLocalDateTime() : null));
        }
        if (legacy.get(0).getFeishuDocUrl() == null || legacy.get(0).getFeishuDocUrl().isBlank()) {
            return;
        }
        String hostAgenda = loadHostAgenda(conn, 1);
        if (HostAgendaJsonCodec.isVersion2(hostAgenda, MAPPER)) {
            return;
        }
        String merged = HostAgendaJsonCodec.mergeDocTableRows(MAPPER, hostAgenda, 1, legacy);
        if (apply && merged != null && !merged.equals(hostAgenda)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE int_meeting_type_preset SET host_agenda = CAST(? AS JSON) WHERE code = 1")) {
                ps.setString(1, merged);
                ps.executeUpdate();
            }
            System.out.println("legacy default -> preset 1 agenda_index=0");
        }
    }

    private static String loadHostAgenda(Connection conn, int code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT host_agenda FROM int_meeting_type_preset WHERE code = ?")) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("host_agenda");
                }
            }
        }
        return null;
    }

    private static List<AgendaDocBindingSnapshot> loadDocRows(Connection conn, int code) throws SQLException {
        List<AgendaDocBindingSnapshot> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, config_name, agenda_index, resource_slot, feishu_doc_url, enabled, config_role,
                       bitable_display_mode, generated_report_url, generated_report_at
                FROM int_matter_progress_doc_config
                WHERE enabled = 1 AND preset_type_code = ?
                ORDER BY agenda_index, resource_slot, id
                """)) {
            ps.setInt(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(AgendaDocBindingSnapshots.of(
                            rs.getLong("id"),
                            rs.getString("config_name"),
                            code,
                            rs.getObject("agenda_index") != null ? rs.getInt("agenda_index") : null,
                            rs.getInt("resource_slot"),
                            rs.getString("feishu_doc_url"),
                            rs.getInt("enabled"),
                            rs.getString("config_role"),
                            rs.getString("bitable_display_mode"),
                            rs.getString("generated_report_url"),
                            rs.getTimestamp("generated_report_at") != null
                                    ? rs.getTimestamp("generated_report_at").toLocalDateTime() : null));
                }
            }
        }
        return out;
    }
}
