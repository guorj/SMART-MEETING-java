package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocxBlockMarkdownExporterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void appendBlock_headingAndBullet() {
        StringBuilder out = new StringBuilder();
        DocxBlockMarkdownExporter.appendBlock(block(4, "heading2", "一、上次会议事项"), out);
        DocxBlockMarkdownExporter.appendBlock(block(5, "heading3", "已完成"), out);
        DocxBlockMarkdownExporter.appendBlock(block(12, "bullet", "事项 A"), out);
        assertThat(out.toString()).contains("## 一、上次会议事项");
        assertThat(out.toString()).contains("### 已完成");
        assertThat(out.toString()).contains("- 事项 A");
    }

    private static ObjectNode block(int type, String field, String text) {
        ObjectNode root = JSON.createObjectNode();
        root.put("block_type", type);
        ObjectNode body = JSON.createObjectNode();
        ObjectNode el = JSON.createObjectNode();
        ObjectNode run = JSON.createObjectNode();
        run.put("content", text);
        el.set("text_run", run);
        body.set("elements", JSON.createArrayNode().add(el));
        root.set(field, body);
        return root;
    }
}
