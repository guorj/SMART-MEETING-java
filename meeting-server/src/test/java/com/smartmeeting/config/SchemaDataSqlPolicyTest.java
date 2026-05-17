package com.smartmeeting.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子脚本策略：重复执行不得用 VALUES(feishu_doc_url) 覆盖已有链接。
 */
class SchemaDataSqlPolicyTest {

    @Test
    void schemaDataDoesNotOverwriteFeishuUrlsOnDuplicate() throws Exception {
        Path sql = Path.of("src/main/resources/schema-data.sql");
        if (!Files.isRegularFile(sql)) {
            sql = Path.of("meeting-server/src/main/resources/schema-data.sql");
        }
        String text = stripSqlComments(Files.readString(sql, StandardCharsets.UTF_8));
        assertFalse(text.contains("feishu_doc_url = VALUES(feishu_doc_url)"),
                "schema-data.sql 不得在 ON DUPLICATE 中覆盖 feishu_doc_url");
        assertFalse(text.contains("host_agenda        = VALUES(host_agenda)"),
                "schema-data.sql 不得在 ON DUPLICATE 中覆盖 host_agenda");
        assertTrue(text.contains("ON DUPLICATE KEY UPDATE"),
                "schema-data.sql 应保留幂等 INSERT");
    }

    private static String stripSqlComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            String t = line.trim();
            if (t.startsWith("--")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
}
