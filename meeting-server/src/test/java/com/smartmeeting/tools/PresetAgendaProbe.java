package com.smartmeeting.tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

/**
 * CLI 探针：查询 preset=1 的 {@code int_meeting_type_preset.host_agenda} 配置快照。
 * <p>
 * 运行: {@code mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.PresetAgendaProbe -Dexec.classpathScope=test}
 */
public final class PresetAgendaProbe {

    /** 连接生产库并打印 preset 1 的 host_agenda JSON。 */
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
