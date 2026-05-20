package com.smartmeeting.service;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.Meeting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link PresetAgendaDocService} 单元测试：验证预设会序文档 URL 填充与多资源槽解析。
 */
class PresetAgendaDocServiceTest {

    /** 议题已有 URL 时不应被配置表覆盖。 */
    @Test
    void enrichHostAgendaItems_doesNotOverrideExistingUrl() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("会序2");
        item.setFeishuDocUrl("https://x.feishu.cn/docx/doxExisting");
        List<HostAgendaItemDto> items = new ArrayList<>();
        items.add(new HostAgendaItemDto());
        items.add(item);

        MatterProgressDocConfig cfg = new MatterProgressDocConfig();
        cfg.setAgendaIndex(1);
        cfg.setFeishuDocUrl("https://x.feishu.cn/docx/doxFromTable");

        PresetAgendaDocService svc = new PresetAgendaDocService(null, null, null) {
            @Override
            public List<MatterProgressDocConfig> listEnabledByPreset(int presetTypeCode) {
                return List.of(cfg);
            }
        };
        svc.enrichHostAgendaItems(1, items);
        assertEquals("https://x.feishu.cn/docx/doxExisting", items.get(1).getFeishuDocUrl());
    }

    /** 议题缺少 URL 时应从配置表填充。 */
    @Test
    void enrichHostAgendaItems_fillsFromConfigWhenMissing() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("会序2");
        List<HostAgendaItemDto> items = List.of(item);

        MatterProgressDocConfig cfg = new MatterProgressDocConfig();
        cfg.setAgendaIndex(0);
        cfg.setResourceSlot(0);
        cfg.setFeishuDocUrl("https://x.feishu.cn/docx/doxFromTable");

        PresetAgendaDocService svc = new PresetAgendaDocService(null, null, null) {
            @Override
            public List<MatterProgressDocConfig> listEnabledByPreset(int presetTypeCode) {
                return List.of(cfg);
            }
        };
        svc.enrichHostAgendaItems(1, items);
        assertEquals(1, items.get(0).getFeishuDocs().size());
        assertEquals("https://x.feishu.cn/docx/doxFromTable", items.get(0).getFeishuDocUrl());
    }

    /** 同一议程多资源槽应全部解析返回。 */
    @Test
    void resolveAllResources_multipleSlotsSameAgenda() {
        MatterProgressDocConfig base = new MatterProgressDocConfig();
        base.setAgendaIndex(1);
        base.setResourceSlot(0);
        base.setFeishuDocUrl("https://x.feishu.cn/base/app1?table=tbl1");

        MatterProgressDocConfig wiki = new MatterProgressDocConfig();
        wiki.setAgendaIndex(1);
        wiki.setResourceSlot(1);
        wiki.setFeishuDocUrl("https://x.feishu.cn/wiki/wiki1");

        PresetAgendaDocService svc = new PresetAgendaDocService(null, null, null) {
            @Override
            public List<MatterProgressDocConfig> listConfigsForAgenda(int presetTypeCode, int agendaIndex) {
                return List.of(base, wiki);
            }
        };
        Meeting meeting = new Meeting();
        meeting.setPresetTypeCode(1);
        var refs = svc.resolveAllResources(meeting, 1, null);
        assertEquals(2, refs.size());
    }

    /** resolveDocumentId 应优先使用运行时 URL。 */
    @Test
    void resolveDocumentId_prefersRuntimeUrl() {
        PresetAgendaDocService svc = new PresetAgendaDocService(null, null, null);
        String id = svc.resolveDocumentId(null, 0, "https://x.feishu.cn/docx/doxRuntime");
        assertEquals("doxRuntime", id);
        assertNull(svc.resolveDocumentId(null, 0, ""));
    }
}
