package com.smartmeeting.admin.agenda;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.entity.MatterProgressDocConfig;
import com.smartmeeting.admin.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.config.agenda.AgendaConfigProvider;
import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.AgendaPresetSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 会序飞书资料绑定 Provider：不负责 preset 主表字段，仅聚合 doc_bindings。
 */
@Component
@RequiredArgsConstructor
public class MatterDocConfigAgendaConfigProvider implements AgendaConfigProvider {

    private final MatterProgressDocConfigMapper configMapper;

    @Override
    public String providerId() {
        return "matter_doc_config";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Optional<AgendaPresetSnapshot> loadPreset(int presetTypeCode) {
        if (presetTypeCode < 1 || presetTypeCode > 5) {
            return Optional.empty();
        }
        List<AgendaDocBindingSnapshot> bindings = listBindings(presetTypeCode);
        return Optional.of(AgendaPresetSnapshot.builder()
                .presetTypeCode(presetTypeCode)
                .docBindings(bindings)
                .build());
    }

    @Override
    public void savePreset(AgendaPresetSnapshot snapshot) {
        // doc 行由 AgendaConfigService 单独 CRUD
    }

    public List<AgendaDocBindingSnapshot> listBindings(int presetTypeCode) {
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getPresetTypeCode, presetTypeCode)
                .orderByAsc(MatterProgressDocConfig::getAgendaIndex)
                .orderByAsc(MatterProgressDocConfig::getResourceSlot)
                .orderByAsc(MatterProgressDocConfig::getId);
        return configMapper.selectList(q).stream().map(this::toSnapshot).collect(Collectors.toList());
    }

    public AgendaDocBindingSnapshot getBinding(long id) {
        MatterProgressDocConfig row = configMapper.selectById(id);
        return row == null ? null : toSnapshot(row);
    }

    public long saveBinding(AgendaDocBindingSnapshot snap) {
        MatterProgressDocConfig row = new MatterProgressDocConfig();
        if (snap.getId() != null) {
            row = configMapper.selectById(snap.getId());
            if (row == null) {
                row = new MatterProgressDocConfig();
            }
        }
        row.setConfigName(snap.getConfigName());
        row.setPresetTypeCode(snap.getPresetTypeCode());
        row.setAgendaIndex(snap.getAgendaIndex());
        row.setResourceSlot(snap.getResourceSlot() != null ? snap.getResourceSlot() : 0);
        row.setFeishuDocUrl(snap.getFeishuDocUrl());
        row.setEnabled(snap.getEnabled() != null ? snap.getEnabled() : 1);
        row.setConfigRole(snap.getConfigRole() != null ? snap.getConfigRole() : "SOURCE");
        row.setBitableDisplayMode(snap.getBitableDisplayMode());
        row.setGeneratedReportUrl(snap.getGeneratedReportUrl());
        if (row.getId() == null) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        return row.getId();
    }

    public void deleteBinding(long id) {
        configMapper.deleteById(id);
    }

    private AgendaDocBindingSnapshot toSnapshot(MatterProgressDocConfig c) {
        return AgendaDocBindingSnapshot.builder()
                .id(c.getId())
                .configName(c.getConfigName())
                .presetTypeCode(c.getPresetTypeCode())
                .agendaIndex(c.getAgendaIndex())
                .resourceSlot(c.getResourceSlot())
                .feishuDocUrl(c.getFeishuDocUrl())
                .enabled(c.getEnabled())
                .configRole(c.getConfigRole())
                .bitableDisplayMode(c.getBitableDisplayMode())
                .generatedReportUrl(c.getGeneratedReportUrl())
                .generatedReportAt(c.getGeneratedReportAt())
                .build();
    }
}
