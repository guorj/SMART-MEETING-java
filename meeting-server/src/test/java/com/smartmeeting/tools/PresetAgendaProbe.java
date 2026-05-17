package com.smartmeeting.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

public final class PresetAgendaProbe {
    public static void main(String[] args) throws Exception {
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(ds.get("url"), ds.get("username"), ds.get("password"));
             Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT code, host_agenda FROM int_meeting_type_preset WHERE code = 1");
            if (rs.next()) {
                System.out.println("preset 1 host_agenda=" + rs.getString("host_agenda"));
            }
        }
    }
}
