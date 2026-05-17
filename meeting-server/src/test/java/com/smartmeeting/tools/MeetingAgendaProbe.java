package com.smartmeeting.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

/** 查询单次会议 host_agenda 中的 feishuDocUrl */
public final class MeetingAgendaProbe {
    public static void main(String[] args) throws Exception {
        String meetingId = args.length > 0 ? args[0] : "896023ae-0979-462b-b792-fd8169fb7b0c";
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(ds.get("url"), ds.get("username"), ds.get("password"))) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, preset_type_code, recording_url, host_agenda FROM int_meeting WHERE id = ?")) {
                ps.setString(1, meetingId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("meeting not found: " + meetingId);
                        return;
                    }
                    System.out.println("id=" + rs.getString("id"));
                    System.out.println("preset_type_code=" + rs.getObject("preset_type_code"));
                    System.out.println("recording_url=" + rs.getString("recording_url"));
                    System.out.println("host_agenda=" + rs.getString("host_agenda"));
                }
            }
        }
    }

    private MeetingAgendaProbe() {
    }
}
