package com.smartmeeting.service.feishu;

import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.config.agenda.AgendaBindingConverter;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;
import com.smartmeeting.entity.MatterProgressDocConfig;

import java.util.List;

/**
 * API DTO 适配层；合并逻辑在 meeting-config-core {@link com.smartmeeting.config.feishu.FeishuDocRefs}。
 */
public final class FeishuDocRefs {

    private FeishuDocRefs() {
    }

    public static FeishuDocRefDto toDto(FeishuResourceRef ref) {
        var host = com.smartmeeting.config.feishu.FeishuDocRefs.toHostRef(ref);
        if (host == null) {
            return null;
        }
        return FeishuDocRefDto.builder()
                .kind(host.getKind())
                .url(host.getUrl())
                .build();
    }

    public static List<FeishuResourceRef> mergeDistinct(List<FeishuResourceRef> refs) {
        return com.smartmeeting.config.feishu.FeishuDocRefs.mergeDistinct(refs);
    }

    public static void addFromUrl(List<FeishuResourceRef> target, String url) {
        com.smartmeeting.config.feishu.FeishuDocRefs.addFromUrl(target, url);
    }

    public static void addFromConfig(List<FeishuResourceRef> target, MatterProgressDocConfig cfg) {
        com.smartmeeting.config.feishu.FeishuDocRefs.addFromBinding(target, AgendaBindingConverter.from(cfg));
    }

    public static void addFromDto(List<FeishuResourceRef> target, FeishuDocRefDto dto) {
        if (dto == null) {
            return;
        }
        addFromUrl(target, dto.getUrl());
    }

    public static String kindLabel(FeishuResourceKind kind) {
        return com.smartmeeting.config.feishu.FeishuDocRefs.kindLabel(kind);
    }
}
