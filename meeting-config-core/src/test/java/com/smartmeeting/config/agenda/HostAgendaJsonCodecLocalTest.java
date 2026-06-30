package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAgendaJsonCodecLocalTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripLocalAndFeishuDocs() {
        HostAgendaDocBinding local = HostAgendaDocBinding.builder()
                .configName("preset1-report-doc")
                .role("SOURCE")
                .slot(0)
                .storageKind(AgendaStorageKind.LOCAL)
                .fileId("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .originalFilename("经营汇报.docx")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .enabled(true)
                .build();
        HostAgendaDocBinding feishu = HostAgendaDocBinding.builder()
                .configName("preset1-weekly")
                .role("SOURCE")
                .slot(1)
                .url("https://example.feishu.cn/docx/abc123")
                .enabled(true)
                .build();
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("朱忠娜汇报");
        item.setMinutes(5);
        item.setDocs(List.of(local, feishu));

        String json = HostAgendaJsonCodec.toJson(mapper, List.of(item));
        assertTrue(json.contains("storageKind") && json.contains("LOCAL"));
        assertTrue(json.contains("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));
        assertTrue(json.contains("example.feishu.cn"));

        List<HostAgendaItem> parsed = HostAgendaJsonCodec.parseItems(mapper, json);
        assertEquals(1, parsed.size());
        assertEquals(2, parsed.get(0).getDocs().size());
        HostAgendaDocBinding pLocal = parsed.get(0).getDocs().get(0);
        assertTrue(pLocal.isLocalStorage());
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", pLocal.getFileId());
        assertEquals("经营汇报.docx", pLocal.getOriginalFilename());
    }

    @Test
    void roundTripOabpTaskSql() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("项目任务通报");
        item.setMinutes(10);
        item.setOabpTaskSql("SELECT task_name FROM jq_project_task_tracking WHERE deleted = 0");

        String json = HostAgendaJsonCodec.toJson(mapper, List.of(item));
        assertTrue(json.contains("oabpTaskSql"));

        List<HostAgendaItem> parsed = HostAgendaJsonCodec.parseItems(mapper, json);
        assertEquals(1, parsed.size());
        assertEquals(
                "SELECT task_name FROM jq_project_task_tracking WHERE deleted = 0",
                parsed.get(0).getOabpTaskSql());
    }

    @Test
    void snapshotRoundTripLocal() {
        AgendaDocBindingSnapshot snap = AgendaDocBindingSnapshot.builder()
                .configName("img-1")
                .storageKind(AgendaStorageKind.LOCAL)
                .fileId("uuid-img")
                .originalFilename("chart.png")
                .mimeType("image/png")
                .resourceSlot(0)
                .configRole("SOURCE")
                .enabled(1)
                .build();
        HostAgendaDocBinding doc = HostAgendaJsonCodec.fromSnapshot(snap);
        assertNotNull(doc);
        assertTrue(doc.isLocalStorage());
        AgendaDocBindingSnapshot back = HostAgendaJsonCodec.toSnapshot(doc, 1, 0);
        assertEquals(AgendaStorageKind.LOCAL, back.getStorageKind());
        assertEquals("uuid-img", back.getFileId());
    }

    @Test
    void roundTripShowInHostOnDoc() {
        HostAgendaDocBinding feishu = HostAgendaDocBinding.builder()
                .configName("preset1-weekly")
                .role("SOURCE")
                .slot(0)
                .url("https://example.feishu.cn/base/abc123")
                .showInHost(false)
                .enabled(true)
                .build();
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("综合通报");
        item.setMinutes(10);
        item.setDocs(List.of(feishu));

        String json = HostAgendaJsonCodec.toJson(mapper, List.of(item));
        assertTrue(json.contains("\"showInHost\":false"));

        List<HostAgendaItem> parsed = HostAgendaJsonCodec.parseItems(mapper, json);
        assertEquals(1, parsed.size());
        assertFalse(parsed.get(0).getDocs().get(0).isShowInHost());
    }
}
