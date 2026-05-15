package com.smartmeeting.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一次性：从 meeting-server 模块根目录运行，读取 application-prod.yml 中的 datasource，拉取 intelligence 库表结构快照。
 * 用法（在 meeting-server 目录下）:
 * <pre>
 *   mvn -q "-DincludeScope=runtime" "-Dmdep.outputFile=target/cp.runtime.txt" dependency:build-classpath
 *   （PowerShell）$cp = Get-Content -Raw target/cp.runtime.txt
 *   javac -encoding UTF-8 -d target/probe-classes -cp $cp src/test/java/com/smartmeeting/tools/ProdSchemaProbe.java
 *   java -cp "$cp;target/probe-classes" com.smartmeeting.tools.ProdSchemaProbe
 * </pre>
 * 也可用环境变量覆盖：PROD_DB_URL、PROD_DB_USER、PROD_DB_PASSWORD。
 */
public final class ProdSchemaProbe {

    public static void main(String[] args) throws Exception {
        String url = envOr("PROD_DB_URL", null);
        String user = envOr("PROD_DB_USER", null);
        String pass = envOr("PROD_DB_PASSWORD", null);
        if (url == null) {
            Path yml = Paths.get("src/main/resources/application-prod.yml");
            if (!Files.isRegularFile(yml)) {
                yml = Paths.get("meeting-server/src/main/resources/application-prod.yml");
            }
            if (!Files.isRegularFile(yml)) {
                System.err.println("未找到 application-prod.yml，请设置 PROD_DB_URL/USER/PASSWORD 或在 meeting-server 目录运行");
                System.exit(2);
            }
            Map<String, String> ds = parseDatasourceYaml(Files.readString(yml, StandardCharsets.UTF_8));
            url = ds.get("url");
            user = ds.get("username");
            pass = ds.get("password");
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        Path out = Paths.get("target/prod-schema-snapshot.json");
        Files.createDirectories(out.getParent());

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement()) {
            String db = extractDbName(url);
            List<Map<String, Object>> tables = new ArrayList<>();
            String sqlTables = "SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT FROM information_schema.tables "
                    + "WHERE table_schema = '" + escapeIdent(db) + "' ORDER BY TABLE_NAME";
            try (ResultSet rs = st.executeQuery(sqlTables)) {
                while (rs.next()) {
                    Map<String, Object> t = new LinkedHashMap<>();
                    String table = rs.getString(1);
                    t.put("name", table);
                    t.put("type", rs.getString(2));
                    t.put("comment", rs.getString(3));
                    // Nested queries must use a separate Statement; same Statement closes this ResultSet.
                    t.put("columns", loadColumns(conn, db, table));
                    t.put("indexes", loadIndexes(conn, db, table));
                    tables.add(t);
                }
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("database", db);
            root.put("probedAt", java.time.Instant.now().toString());
            root.put("tables", tables);
            String json = toJson(root);
            Files.writeString(out, json, StandardCharsets.UTF_8);
            System.out.println("Wrote " + out.toAbsolutePath() + " (" + tables.size() + " tables)");
        }
    }

    private static String envOr(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? dflt : v.trim();
    }

    private static Map<String, String> parseDatasourceYaml(String text) {
        Map<String, String> m = new LinkedHashMap<>();
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

    private static String extractDbName(String jdbcUrl) {
        int i = jdbcUrl.indexOf("/", jdbcUrl.indexOf("://") + 3);
        int q = jdbcUrl.indexOf('?', i);
        if (i < 0) {
            return "intelligence";
        }
        String sub = jdbcUrl.substring(i + 1, q > 0 ? q : jdbcUrl.length());
        return sub.isEmpty() ? "intelligence" : sub;
    }

    private static String escapeIdent(String s) {
        return s.replace("'", "''");
    }

    private static List<Map<String, Object>> loadColumns(Connection conn, String db, String table) throws Exception {
        List<Map<String, Object>> cols = new ArrayList<>();
        String q = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT, EXTRA, ORDINAL_POSITION "
                + "FROM information_schema.columns WHERE table_schema = '" + escapeIdent(db) + "' AND table_name = '"
                + escapeIdent(table) + "' ORDER BY ORDINAL_POSITION";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("name", rs.getString(1));
                c.put("type", rs.getString(2));
                c.put("nullable", rs.getString(3));
                c.put("default", rs.getString(4));
                c.put("comment", rs.getString(5));
                c.put("extra", rs.getString(6));
                cols.add(c);
            }
        }
        return cols;
    }

    private static List<Map<String, Object>> loadIndexes(Connection conn, String db, String table) throws Exception {
        List<Map<String, Object>> idx = new ArrayList<>();
        String q = "SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME, SEQ_IN_INDEX FROM information_schema.statistics "
                + "WHERE table_schema = '" + escapeIdent(db) + "' AND table_name = '" + escapeIdent(table)
                + "' ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(q)) {
            while (rs.next()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("index", rs.getString(1));
                r.put("nonUnique", rs.getString(2));
                r.put("column", rs.getString(3));
                r.put("seq", rs.getString(4));
                idx.add(r);
            }
        }
        return idx;
    }

    private static String toJson(Object o) {
        return toJson0(o, 0);
    }

    @SuppressWarnings("unchecked")
    private static String toJson0(Object o, int depth) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String s) {
            return "\"" + esc(s) + "\"";
        }
        if (o instanceof Number || o instanceof Boolean) {
            return o.toString();
        }
        if (o instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("  ".repeat(depth + 1)).append(toJson0(list.get(i), depth + 1));
                if (i + 1 < list.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ".repeat(depth)).append("]");
            return sb.toString();
        }
        if (o instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append("  ".repeat(depth + 1)).append("\"").append(esc(String.valueOf(e.getKey()))).append("\": ");
                sb.append(toJson0(e.getValue(), depth + 1));
                if (++i < map.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ".repeat(depth)).append("}");
            return sb.toString();
        }
        return "\"" + esc(String.valueOf(o)) + "\"";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private ProdSchemaProbe() {
    }
}
