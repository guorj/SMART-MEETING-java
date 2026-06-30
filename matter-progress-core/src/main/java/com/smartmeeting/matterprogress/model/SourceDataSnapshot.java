package com.smartmeeting.matterprogress.model;

/** oabp SOURCE 读取结果（Legacy 路径 Java 预查；MCP 路径由 Agent 自行查库） */
public record SourceDataSnapshot(
        String configName,
        String oabpTaskSql,
        String oabpSchemaHint,
        String plainText
) {
}
