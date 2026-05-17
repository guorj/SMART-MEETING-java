package com.smartmeeting.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * 在生产库手工执行 SQL 迁移（默认仅预览，不写库）。
 * 用法（meeting-server 目录）:
 * <pre>
 *   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.ProdSchemaMigrate -Dexec.classpathScope=test
 *   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.ProdSchemaMigrate -Dexec.classpathScope=test -Dexec.args="--apply"
 *   mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.ProdSchemaMigrate -Dexec.classpathScope=test -Dexec.args="--apply target/fix-preset1-agenda23.sql"
 * </pre>
 */
public final class ProdSchemaMigrate {

    public static void main(String[] args) throws Exception {
        boolean apply = containsFlag(args, "--apply");
        boolean dryRun = !apply;
        Map<String, String> ds = ProdSchemaProbeDatasource.load();
        String url = ds.get("url");
        if (!url.contains("allowMultiQueries")) {
            url = url + (url.contains("?") ? "&" : "?") + "allowMultiQueries=true";
        }
        Path sql;
        String sqlArg = firstNonFlagArg(args);
        if (sqlArg != null) {
            sql = Paths.get(sqlArg);
        } else {
            sql = Paths.get("src/main/resources/schema-upgrade-v0.4-prod.sql");
            if (!Files.isRegularFile(sql)) {
                sql = Paths.get("meeting-server/src/main/resources/schema-upgrade-v0.4-prod.sql");
            }
        }
        String script = Files.readString(sql, StandardCharsets.UTF_8);
        if (script.startsWith("\uFEFF")) {
            script = script.substring(1);
        }
        System.out.println("Script: " + sql.toAbsolutePath() + " (" + script.length() + " chars), dryRun=" + dryRun);
        if (dryRun) {
            System.out.println("Dry run — no changes applied. Pass --apply to execute.");
            return;
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(url, ds.get("username"), ds.get("password"))) {
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                st.execute(script);
            } catch (SQLException e) {
                System.err.println("Migration failed: " + e.getMessage());
                throw e;
            }
        }
        System.out.println("Migration completed.");
    }

    private static boolean containsFlag(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String a : args) {
            if (flag.equals(a)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonFlagArg(String[] args) {
        if (args == null) {
            return null;
        }
        for (String a : args) {
            if (a != null && !a.startsWith("--")) {
                return a;
            }
        }
        return null;
    }

    private ProdSchemaMigrate() {
    }
}
