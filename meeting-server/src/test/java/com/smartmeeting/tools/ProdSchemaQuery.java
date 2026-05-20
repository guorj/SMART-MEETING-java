package com.smartmeeting.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 一次性 CLI 探针：查询生产库关键表行数与 matter_progress 配置快照。 */
public final class ProdSchemaQuery {

    /** 执行多项诊断 SQL 并打印结果行。 */
    public static void main(String[] args) throws Exception {
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(ds.get("url"), ds.get("username"), ds.get("password"));
             Statement st = conn.createStatement()) {
            exec(st, "SELECT COUNT(*) AS c FROM int_matter_progress_doc_config");
            exec(st, "SELECT id, config_name, preset_type_code, agenda_index, resource_slot, "
                    + "SUBSTRING(feishu_doc_url, 1, 80) AS url_prefix, enabled "
                    + "FROM int_matter_progress_doc_config WHERE preset_type_code = 1 "
                    + "ORDER BY agenda_index, resource_slot, id");
            exec(st, "SELECT preset_type_code, agenda_index, COUNT(*) AS cnt "
                    + "FROM int_matter_progress_doc_config WHERE preset_type_code IS NOT NULL "
                    + "GROUP BY preset_type_code, agenda_index HAVING cnt > 1");
            exec(st, "SHOW COLUMNS FROM int_matter_progress_doc_config LIKE 'resource_slot'");
            exec(st, "SELECT code, JSON_LENGTH(host_agenda, '$.items') AS host_items "
                    + "FROM int_meeting_type_preset ORDER BY code");
        }
    }

    private static void exec(Statement st, String sql) throws Exception {
        System.out.println("--- " + sql);
        try (ResultSet rs = st.executeQuery(sql)) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        row.append(" | ");
                    }
                    row.append(rs.getMetaData().getColumnLabel(i)).append('=').append(rs.getString(i));
                }
                System.out.println(row);
            }
        }
        System.out.println();
    }
}

/** 生产库数据源加载器：从环境变量或 {@code application-prod.yml} 解析 JDBC 连接信息。 */
final class ProdSchemaProbeDatasource {

    /** 加载 url/username/password，优先使用 PROD_DB_* 环境变量。 */
    static Map<String, String> load() throws Exception {
        String url = envOr("PROD_DB_URL", null);
        String user = envOr("PROD_DB_USER", null);
        String pass = envOr("PROD_DB_PASSWORD", null);
        if (url == null) {
            Path yml = Paths.get("src/main/resources/application-prod.yml");
            if (!Files.isRegularFile(yml)) {
                yml = Paths.get("meeting-server/src/main/resources/application-prod.yml");
            }
            String text = Files.readString(yml, StandardCharsets.UTF_8);
            Map<String, String> m = new java.util.LinkedHashMap<>();
            Pattern pUrl = Pattern.compile("^\\s*url:\\s*(.+?)\\s*$", Pattern.MULTILINE);
            Pattern pUser = Pattern.compile("^\\s*username:\\s*(.+?)\\s*$", Pattern.MULTILINE);
            Pattern pPass = Pattern.compile("^\\s*password:\\s*\"?([^\"\\n]+)\"?\\s*$", Pattern.MULTILINE);
            Matcher mu = pUrl.matcher(text);
            if (mu.find()) {
                m.put("url", mu.group(1).trim());
            }
            mu = pUser.matcher(text);
            if (mu.find()) {
                m.put("username", mu.group(1).trim());
            }
            mu = pPass.matcher(text);
            if (mu.find()) {
                m.put("password", mu.group(1).trim());
            }
            return m;
        }
        return Map.of("url", url, "username", user, "password", pass);
    }

    private static String envOr(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? dflt : v.trim();
    }

    private ProdSchemaProbeDatasource() {
    }
}
