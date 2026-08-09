package com.smartmeeting.config.agenda;

import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;

import java.util.ArrayList;
import java.util.List;

public final class HostAgendaDtoConverter {

    private HostAgendaDtoConverter() {
    }

    public static HostAgendaItem toCore(HostAgendaItemDto dto) {
        if (dto == null) {
            return null;
        }
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle(dto.getTitle());
        item.setMinutes(dto.getMinutes());
        item.setDetail(dto.getDetail());
        item.setFeishuDocUrl(dto.getFeishuDocUrl());
        copyOabpFields(dto, item);
        if (dto.getFeishuDocs() != null) {
            List<HostAgendaFeishuDocRef> refs = new ArrayList<>();
            for (FeishuDocRefDto r : dto.getFeishuDocs()) {
                if (r == null) {
                    continue;
                }
                refs.add(HostAgendaFeishuDocRef.builder().kind(r.getKind()).url(r.getUrl()).build());
            }
            item.setFeishuDocs(refs);
        }
        return item;
    }

    public static List<HostAgendaItem> toCoreList(List<HostAgendaItemDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<HostAgendaItem> out = new ArrayList<>();
        for (HostAgendaItemDto d : dtos) {
            HostAgendaItem item = toCore(d);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** 与 DTO 列表等长，保留会序下标（含占位 null 项）。 */
    public static List<HostAgendaItem> toCoreListAligned(List<HostAgendaItemDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<HostAgendaItem> out = new ArrayList<>(dtos.size());
        for (HostAgendaItemDto d : dtos) {
            out.add(d == null ? null : toCore(d));
        }
        return out;
    }

    public static List<HostAgendaItemDto> toDtoList(List<HostAgendaItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<HostAgendaItemDto> out = new ArrayList<>();
        for (HostAgendaItem item : items) {
            HostAgendaItemDto dto = new HostAgendaItemDto();
            dto.setTitle(item.getTitle());
            dto.setMinutes(item.getMinutes());
            dto.setDetail(item.getDetail());
            dto.setFeishuDocUrl(item.getFeishuDocUrl());
            copyOabpFields(item, dto);
            if (item.getFeishuDocs() != null) {
                List<FeishuDocRefDto> refs = new ArrayList<>();
                for (HostAgendaFeishuDocRef r : item.getFeishuDocs()) {
                    if (r == null) {
                        continue;
                    }
                    refs.add(FeishuDocRefDto.builder().kind(r.getKind()).url(r.getUrl()).build());
                }
                dto.setFeishuDocs(refs);
            }
            out.add(dto);
        }
        return out;
    }

    public static void copyFeishuFields(HostAgendaItem core, HostAgendaItemDto dto) {
        if (core == null || dto == null) {
            return;
        }
        dto.setFeishuDocUrl(core.getFeishuDocUrl());
        if (core.getFeishuDocs() != null) {
            List<FeishuDocRefDto> refs = new ArrayList<>();
            for (HostAgendaFeishuDocRef r : core.getFeishuDocs()) {
                if (r == null) {
                    continue;
                }
                refs.add(FeishuDocRefDto.builder().kind(r.getKind()).url(r.getUrl()).build());
            }
            dto.setFeishuDocs(refs.isEmpty() ? null : refs);
        }
    }

    /** 将 core 的 oabp 字段回写到 DTO（enrich 后回写用）。 */
    public static void copyOabpFields(HostAgendaItem core, HostAgendaItemDto dto) {
        if (core == null || dto == null) {
            return;
        }
        if (core.getOabpTaskSql() != null && !core.getOabpTaskSql().isBlank()) {
            dto.setOabpTaskSql(core.getOabpTaskSql());
        }
        if (core.getOabpTaskShow() != null) {
            dto.setOabpTaskShow(core.getOabpTaskShow());
        }
        if (core.getOabpDisplayTemplate() != null && !core.getOabpDisplayTemplate().isEmpty()) {
            dto.setOabpDisplayTemplate(core.getOabpDisplayTemplate());
        }
        if (core.getOabpSqlPresetId() != null && !core.getOabpSqlPresetId().isBlank()) {
            dto.setOabpSqlPresetId(core.getOabpSqlPresetId());
        }
    }

    /** 将 DTO 的 oabp 字段写入 core（toCore 用）。 */
    private static void copyOabpFields(HostAgendaItemDto dto, HostAgendaItem item) {
        if (dto == null || item == null) {
            return;
        }
        item.setOabpTaskSql(dto.getOabpTaskSql());
        item.setOabpTaskShow(dto.getOabpTaskShow());
        item.setOabpDisplayTemplate(dto.getOabpDisplayTemplate());
        item.setOabpSqlPresetId(dto.getOabpSqlPresetId());
    }

    public static List<HostAgendaFeishuDocRef> runtimeDocsFromDto(List<FeishuDocRefDto> runtimeDocs) {
        if (runtimeDocs == null) {
            return null;
        }
        List<HostAgendaFeishuDocRef> out = new ArrayList<>();
        for (FeishuDocRefDto d : runtimeDocs) {
            if (d == null) {
                continue;
            }
            out.add(HostAgendaFeishuDocRef.builder().kind(d.getKind()).url(d.getUrl()).build());
        }
        return out;
    }
}
