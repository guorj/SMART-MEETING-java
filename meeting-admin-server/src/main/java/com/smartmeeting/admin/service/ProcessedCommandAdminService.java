package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.entity.ProcessedCommand;
import com.smartmeeting.admin.repository.ProcessedCommandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcessedCommandAdminService {

    private final ProcessedCommandMapper processedCommandMapper;

    public Page<ProcessedCommand> list(int page, int size, String commandType,
                                       String aggregateType, String aggregateId, String commandKey) {
        Page<ProcessedCommand> p = new Page<>(page, size);
        LambdaQueryWrapper<ProcessedCommand> w = new LambdaQueryWrapper<ProcessedCommand>()
                .eq(commandType != null && !commandType.isBlank(), ProcessedCommand::getCommandType, commandType)
                .eq(aggregateType != null && !aggregateType.isBlank(), ProcessedCommand::getAggregateType, aggregateType)
                .eq(aggregateId != null && !aggregateId.isBlank(), ProcessedCommand::getAggregateId, aggregateId)
                .eq(commandKey != null && !commandKey.isBlank(), ProcessedCommand::getCommandKey, commandKey)
                .orderByDesc(ProcessedCommand::getId);
        return processedCommandMapper.selectPage(p, w);
    }

    public Map<String, Object> stats() {
        Long total = processedCommandMapper.selectCount(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        return out;
    }
}
