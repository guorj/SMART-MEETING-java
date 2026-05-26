package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.service.cache.MeetingPresetCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        PresetAgendaDocService svc = new PresetAgendaDocService(presetMapper, presetCache, null, JSON);
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
        PresetAgendaDocService svc = new PresetAgendaDocService(presetMapper, presetCache, null, JSON);
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
        PresetAgendaDocService svc = new PresetAgendaDocService(presetMapper, presetCache, null, JSON);
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
        PresetAgendaDocService svc = new PresetAgendaDocService(presetMapper, presetCache, null, JSON);
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
        PresetAgendaDocService svc = new PresetAgendaDocService(presetMapper, presetCache, null, JSON);
        svc.enrichHostAgendaItems(1, items);
        assertEquals("https://x.feishu.cn/docx/doxFromPreset", items.get(0).getFeishuDocUrl());
    }

    @Test
    void resolveDocumentId_prefersRuntimeUrl() {
        PresetAgendaDocService svc = new PresetAgendaDocService(null, presetCache, null, JSON);
        String id = svc.resolveDocumentId(null, 0, "https://x.feishu.cn/docx/doxRuntime");
        assertEquals("doxRuntime", id);
        assertNull(svc.resolveDocumentId(null, 0, ""));
    }
}
