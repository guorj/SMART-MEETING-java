package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.service.cache.MeetingPresetCacheService;
import com.smartmeeting.service.oabp.OabpAgendaTaskPartBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresetAgendaDocServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private MeetingPresetCacheService presetCache;

    @BeforeEach
    void clearPresetCache() {
        presetCache = new MeetingPresetCacheService(JSON, null);
        for (int code = 1; code <= 5; code++) {
            presetCache.evictPreset(code);
        }
    }

    private PresetAgendaDocService newService(MeetingTypePresetMapper presetMapper) {
        return new PresetAgendaDocService(
                presetMapper, presetCache, null, null, JSON, new OabpAgendaTaskPartBuilder(JSON, null));
    }

    @Test
    void enrichHostAgendaItems_doesNotOverrideExistingUrl() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("会序2");
        item.setFeishuDocUrl("https://x.feishu.cn/docx/doxExisting");
        List<HostAgendaItemDto> items = new ArrayList<>();
        items.add(new HostAgendaItemDto());
        items.add(item);

        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"version":2,"items":[{"title":"x"},{"title":"会序2","docs":[{"role":"SOURCE","slot":0,"url":"https://x.feishu.cn/docx/doxFromJson","enabled":true}]}]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);
        svc.enrichHostAgendaItems(1, items);
        assertEquals("https://x.feishu.cn/docx/doxExisting", items.get(1).getFeishuDocUrl());
    }

    @Test
    void enrichHostAgendaItems_fillsFromEmbeddedDocsWhenMissing() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("会序2");
        List<HostAgendaItemDto> items = List.of(item);

        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"version":2,"items":[{"title":"会序2","docs":[{"role":"SOURCE","slot":0,"url":"https://x.feishu.cn/docx/doxFromJson","enabled":true}]}]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);
        svc.enrichHostAgendaItems(1, items);
        assertEquals(1, items.get(0).getFeishuDocs().size());
        assertEquals("https://x.feishu.cn/docx/doxFromJson", items.get(0).getFeishuDocUrl());
    }

    @Test
    void resolveAllResources_multipleSlotsSameAgenda() {
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"version":2,"items":[{"title":"x"},{"title":"会序2","docs":[
                {"role":"SOURCE","slot":0,"url":"https://x.feishu.cn/base/app1?table=tbl1","enabled":true},
                {"role":"SOURCE","slot":1,"url":"https://x.feishu.cn/wiki/wiki1","enabled":true}
                ]}]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);
        Meeting meeting = new Meeting();
        meeting.setPresetTypeCode(1);
        var refs = svc.resolveAllResources(meeting, 1, null);
        assertEquals(2, refs.size());
    }

    @Test
    void resolveAllResources_meetingHostAgendaOverridesPreset() {
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromPreset"}]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);
        Meeting meeting = new Meeting();
        meeting.setPresetTypeCode(1);
        meeting.setHostAgenda("""
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromMeetingRecord"}]}
                """);
        var refs = svc.resolveAllResources(meeting, 0, null);
        assertEquals(1, refs.size());
        assertEquals("doxFromMeetingRecord", refs.get(0).primaryToken());
    }

    @Test
    void enrichHostAgendaItems_prefersPresetTemplate() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("会序1");
        List<HostAgendaItemDto> items = List.of(item);

        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"items":[{"title":"会序1","feishuDocUrl":"https://x.feishu.cn/docx/doxFromPreset"}]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);
        svc.enrichHostAgendaItems(1, items);
        assertEquals("https://x.feishu.cn/docx/doxFromPreset", items.get(0).getFeishuDocUrl());
    }

    @Test
    void resolveDocumentId_prefersRuntimeUrl() {
        PresetAgendaDocService svc = new PresetAgendaDocService(
                null, presetCache, null, null, JSON, new OabpAgendaTaskPartBuilder(JSON, null));
        String id = svc.resolveDocumentId(null, 0, "https://x.feishu.cn/docx/doxRuntime");
        assertEquals("doxRuntime", id);
        assertNull(svc.resolveDocumentId(null, 0, ""));
    }

    @Test
    void buildAgendaDocContent_oabpOnlyNoBindings_returnsOabpPart() {
        // preset 模板：会序项仅有 oabpTaskSql，无任何 docs/feishu 绑定
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"version":2,"items":[
                  {"title":"管小慧汇报","minutes":4,
                   "oabpTaskSql":"SELECT task_name AS 待办事项 FROM jq_todos_task",
                   "oabpTaskShow":true}
                ]}
                """);
        MeetingTypePresetMapper presetMapper = mock(MeetingTypePresetMapper.class);
        when(presetMapper.selectById(1)).thenReturn(preset);
        PresetAgendaDocService svc = newService(presetMapper);

        Meeting meeting = new Meeting();
        meeting.setPresetTypeCode(1);
        // 会议快照为空 -> builder 回退读 preset
        meeting.setHostAgenda(null);

        var resp = svc.buildAgendaDocContent(meeting, 0, "管小慧汇报", null);
        assertNotNull(resp);
        assertEquals(0, resp.getAgendaIndex());
        assertNotNull(resp.getParts());
        assertFalse(resp.getParts().isEmpty(), "OABP part must be returned even with no bindings");
        assertEquals("OABP", resp.getParts().get(0).getDocKind());
    }
}
