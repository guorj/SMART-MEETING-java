package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.admin.entity.PushLogReadUser;
import com.smartmeeting.admin.entity.PushTaskExcludeDate;
import com.smartmeeting.admin.entity.PushTaskExtraDate;
import com.smartmeeting.admin.entity.PushTaskTarget;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.PushLogReadUserMapper;
import com.smartmeeting.admin.repository.PushTaskExcludeDateMapper;
import com.smartmeeting.admin.repository.PushTaskExtraDateMapper;
import com.smartmeeting.admin.repository.PushTaskTargetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushSubTableAdminService {

    private final PushLogReadUserMapper readUserMapper;
    private final PushTaskTargetMapper targetMapper;
    private final PushTaskExtraDateMapper extraDateMapper;
    private final PushTaskExcludeDateMapper excludeDateMapper;

    public List<PushLogReadUser> listReadUsers(String pushLogId) {
        return readUserMapper.selectList(new LambdaQueryWrapper<PushLogReadUser>()
                .eq(pushLogId != null && !pushLogId.isBlank(), PushLogReadUser::getPushLogId, pushLogId)
                .orderByDesc(PushLogReadUser::getReadAt));
    }

    public List<PushTaskTarget> listTargets(String taskId) {
        return targetMapper.selectList(new LambdaQueryWrapper<PushTaskTarget>()
                .eq(taskId != null && !taskId.isBlank(), PushTaskTarget::getTaskId, taskId)
                .orderByAsc(PushTaskTarget::getSortOrder));
    }

    @Transactional
    public String addTarget(String taskId, String targetType, String targetId, Integer sortOrder) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException("taskId 必填");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new BusinessException("targetType 必填");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new BusinessException("targetId 必填");
        }
        PushTaskTarget row = new PushTaskTarget();
        row.setId(UUID.randomUUID().toString());
        row.setTaskId(taskId);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setSortOrder(sortOrder == null ? 0 : sortOrder);
        targetMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void removeTarget(String targetRowId) {
        PushTaskTarget existing = targetMapper.selectById(targetRowId);
        if (existing == null) {
            throw new BusinessException("接收方不存在: " + targetRowId);
        }
        targetMapper.deleteById(targetRowId);
    }

    public List<PushTaskExtraDate> listExtraDates(String taskId) {
        return extraDateMapper.selectList(new LambdaQueryWrapper<PushTaskExtraDate>()
                .eq(taskId != null && !taskId.isBlank(), PushTaskExtraDate::getTaskId, taskId)
                .orderByAsc(PushTaskExtraDate::getExtraDate));
    }

    @Transactional
    public String addExtraDate(String taskId, LocalDate date) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException("taskId 必填");
        }
        if (date == null) {
            throw new BusinessException("extraDate 必填");
        }
        Long existing = extraDateMapper.selectCount(new LambdaQueryWrapper<PushTaskExtraDate>()
                .eq(PushTaskExtraDate::getTaskId, taskId)
                .eq(PushTaskExtraDate::getExtraDate, date));
        if (existing != null && existing > 0) {
            throw new BusinessException("该额外推送日已存在: " + date);
        }
        PushTaskExtraDate row = new PushTaskExtraDate();
        row.setId(UUID.randomUUID().toString());
        row.setTaskId(taskId);
        row.setExtraDate(date);
        extraDateMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void removeExtraDate(String rowId) {
        extraDateMapper.deleteById(rowId);
    }

    public List<PushTaskExcludeDate> listExcludeDates(String taskId) {
        return excludeDateMapper.selectList(new LambdaQueryWrapper<PushTaskExcludeDate>()
                .eq(taskId != null && !taskId.isBlank(), PushTaskExcludeDate::getTaskId, taskId)
                .orderByAsc(PushTaskExcludeDate::getExcludeDate));
    }

    @Transactional
    public String addExcludeDate(String taskId, LocalDate date) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException("taskId 必填");
        }
        if (date == null) {
            throw new BusinessException("excludeDate 必填");
        }
        Long existing = excludeDateMapper.selectCount(new LambdaQueryWrapper<PushTaskExcludeDate>()
                .eq(PushTaskExcludeDate::getTaskId, taskId)
                .eq(PushTaskExcludeDate::getExcludeDate, date));
        if (existing != null && existing > 0) {
            throw new BusinessException("该排除推送日已存在: " + date);
        }
        PushTaskExcludeDate row = new PushTaskExcludeDate();
        row.setId(UUID.randomUUID().toString());
        row.setTaskId(taskId);
        row.setExcludeDate(date);
        excludeDateMapper.insert(row);
        return row.getId();
    }

    @Transactional
    public void removeExcludeDate(String rowId) {
        excludeDateMapper.deleteById(rowId);
    }
}
