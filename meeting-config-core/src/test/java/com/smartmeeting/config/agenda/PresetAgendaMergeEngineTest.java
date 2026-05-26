package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresetAgendaMergeEngineTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void enrich_doesNotOverrideExistingUrl() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("会序2");
        item.setFeishuDocUrl("https://x.feishu.cn/docx/doxExisting");
        List<HostAgendaItem> items = new ArrayList<>();
        items.add(new HostAgendaItem());
        items.add(item);

        AgendaDocBindingSnapshot cfg = AgendaDocBindingSnapshot.builder()
                .agendaIndex(1)
                .feishuDocUrl("https://x.feishu.cn/docx/doxFromTable")
                .configRole("SOURCE")
                .build();

        PresetAgendaMergeEngine.enrichHostAgendaItems(1, null, items, List.of(cfg));
        assertEquals("https://x.feishu.cn/docx/doxExisting", items.get(1).getFeishuDocUrl());
    }

    @Test
    void enrich_fillsFromConfigWhenMissing() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("会序2");
        List<HostAgendaItem> items = List.of(item);

        AgendaDocBindingSnapshot cfg = AgendaDocBindingSnapshot.builder()
                .agendaIndex(0)
                .resourceSlot(0)
                .feishuDocUrl("https://x.feishu.cn/docx/doxFromTable")
                .configRole("SOURCE")
                .build();

        PresetAgendaMergeEngine.enrichHostAgendaItems(1, null, items, List.of(cfg));
        assertEquals(1, items.get(0).getFeishuDocs().size());
        assertEquals("https://x.feishu.cn/docx/doxFromTable", items.get(0).getFeishuDocUrl());
    }

    @Test
    void resolveAllResources_multipleSlotsSameAgenda() {
        AgendaDocBindingSnapshot base = AgendaDocBindingSnapshot.builder()
                .agendaIndex(1)
                .resourceSlot(0)
                .feishuDocUrl("https://x.feishu.cn/base/app1?table=tbl1")
                .configRole("SOURCE")
                .build();
        AgendaDocBindingSnapshot wiki = AgendaDocBindingSnapshot.builder()
                .agendaIndex(1)
                .resourceSlot(1)
                .feishuDocUrl("https://x.feishu.cn/wiki/wiki1")
                .configRole("SOURCE")
                .build();

        var refs = PresetAgendaMergeEngine.resolveAllResources(null, 1, null, List.of(base, wiki), 1, null);
        assertEquals(2, refs.size());
    }

    @Test
    void resolveAllResources_meetingHostAgendaOverridesPresetAndConfig() {
        AgendaDocBindingSnapshot cfg = AgendaDocBindingSnapshot.builder()
                .agendaIndex(0)
                .feishuDocUrl("https://x.feishu.cn/docx/doxFromTable")
                .configRole("SOURCE")
                .build();
        String presetJson = """
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromPreset"}]}
                """;
        String meetingJson = """
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromMeetingRecord"}]}
                """;

        var refs = PresetAgendaMergeEngine.resolveAllResources(meetingJson, 1, presetJson, List.of(cfg), 0, null);
        assertEquals(1, refs.size());
        assertEquals("doxFromMeetingRecord", refs.get(0).primaryToken());
    }

    @Test
    void enrich_prefersPresetTemplateThenConfigTable() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("会序1");
        List<HostAgendaItem> items = List.of(item);

        AgendaDocBindingSnapshot cfg = AgendaDocBindingSnapshot.builder()
                .agendaIndex(0)
                .feishuDocUrl("https://x.feishu.cn/docx/doxFromTable")
                .configRole("SOURCE")
                .build();
        String presetJson = """
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromPreset"}]}
                """;

        PresetAgendaMergeEngine.enrichHostAgendaItems(1, presetJson, items, List.of(cfg));
        assertEquals("https://x.feishu.cn/docx/doxFromPreset", items.get(0).getFeishuDocUrl());
    }

    @Test
    void hostAgendaV2_roundTrip_docsEmbedded() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("会序1");
        item.setMinutes(5);
        item.setDocs(List.of(HostAgendaDocBinding.builder()
                .configName("preset1-weekly-report-out")
                .role("OUTPUT")
                .slot(0)
                .url("https://x.feishu.cn/base/app1")
                .enabled(true)
                .build()));
        String json = PresetAgendaMergeEngine.toHostAgendaJson(JSON, List.of(item));
        var parsed = PresetAgendaMergeEngine.parseHostAgendaItems(JSON, json);
        assertEquals(1, parsed.size());
        assertEquals(1, parsed.get(0).getDocs().size());
        assertEquals("preset1-weekly-report-out", parsed.get(0).getDocs().get(0).getConfigName());
        var report = PresetAgendaMergeEngine.findReportBindingInHostAgenda(json, 0, JSON);
        assert report.isPresent();
        assertEquals("https://x.feishu.cn/base/app1", report.get().outputFeishuDocUrl());
    }

    @Test
    void resolveAllResources_prefersRuntimeUrl() {
        List<HostAgendaFeishuDocRef> runtime = List.of(
                HostAgendaFeishuDocRef.builder().url("https://x.feishu.cn/docx/doxRuntime").build());
        var refs = PresetAgendaMergeEngine.resolveAllResources(null, null, null, List.of(), 0, runtime);
        assertEquals(1, refs.size());
        assertEquals("doxRuntime", refs.get(0).primaryToken());
    }
}
