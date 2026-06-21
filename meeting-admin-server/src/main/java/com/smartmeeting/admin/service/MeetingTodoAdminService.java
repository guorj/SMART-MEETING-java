package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.MeetingTodoAttachmentDto;
import com.smartmeeting.admin.api.dto.MeetingTodoAuditDto;
import com.smartmeeting.admin.api.dto.MeetingTodoDto;
import com.smartmeeting.admin.api.dto.MeetingTodoProgressDto;
import com.smartmeeting.admin.api.dto.MeetingTodoSaveDto;
import com.smartmeeting.admin.entity.MeetingParticipant;
import com.smartmeeting.admin.entity.MeetingTodo;
import com.smartmeeting.admin.entity.MeetingTodoAttachment;
import com.smartmeeting.admin.entity.MeetingTodoAudit;
import com.smartmeeting.admin.entity.MeetingTodoProgress;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingParticipantMapper;
import com.smartmeeting.admin.repository.MeetingTodoAttachmentMapper;
import com.smartmeeting.admin.repository.MeetingTodoAuditMapper;
import com.smartmeeting.admin.repository.MeetingTodoMapper;
import com.smartmeeting.admin.repository.MeetingTodoProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MeetingTodoAdminService {

    private final MeetingTodoMapper todoMapper;
    private final MeetingTodoAuditMapper auditMapper;
    private final MeetingTodoProgressMapper progressMapper;
    private final MeetingTodoAttachmentMapper attachmentMapper;
    private final MeetingParticipantMapper participantMapper;

    public Page<MeetingTodoDto> list(int page, int size, String meetingId, String assigneeId,
                                     String status, Integer presetTypeCode, String keyword) {
        Page<MeetingTodo> p = new Page<>(page, size);
        LambdaQueryWrapper<MeetingTodo> w = new LambdaQueryWrapper<MeetingTodo>()
                .eq(meetingId != null && !meetingId.isBlank(), MeetingTodo::getMeetingId, meetingId)
                .eq(assigneeId != null && !assigneeId.isBlank(), MeetingTodo::getAssigneeId, assigneeId)
                .eq(status != null && !status.isBlank(), MeetingTodo::getStatus, status)
                .eq(presetTypeCode != null, MeetingTodo::getPresetTypeCode, presetTypeCode)
                .and(keyword != null && !keyword.isBlank(),
                        k -> k.like(MeetingTodo::getContent, keyword)
                                .or().like(MeetingTodo::getAssigneeName, keyword)
                                .or().like(MeetingTodo::getOperatorName, keyword))
                .orderByDesc(MeetingTodo::getCreatedAt)
                .orderByDesc(MeetingTodo::getId);
        Page<MeetingTodo> result = todoMapper.selectPage(p, w);
        Page<MeetingTodoDto> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(result.getRecords().stream().map(this::toDto).toList());
        return out;
    }

    public MeetingTodoDto get(String id) {
        MeetingTodo row = todoMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("待办不存在: " + id);
        }
        return toDto(row);
    }

    public List<MeetingTodoAuditDto> listAudit(String todoId) {
        LambdaQueryWrapper<MeetingTodoAudit> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodoAudit::getTodoId, todoId).orderByDesc(MeetingTodoAudit::getCreatedAt);
        return auditMapper.selectList(w).stream().map(this::toAuditDto).toList();
    }

    public List<MeetingTodoProgressDto> listProgress(String todoId) {
        LambdaQueryWrapper<MeetingTodoProgress> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodoProgress::getTodoId, todoId).orderByAsc(MeetingTodoProgress::getCreatedAt);
        return progressMapper.selectList(w).stream().map(this::toProgressDto).toList();
    }

    public List<MeetingTodoAttachmentDto> listAttachments(String todoId) {
        LambdaQueryWrapper<MeetingTodoAttachment> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodoAttachment::getTodoId, todoId).orderByAsc(MeetingTodoAttachment::getCreatedAt);
        return attachmentMapper.selectList(w).stream().map(this::toAttachmentDto).toList();
    }

    @Transactional
    public String save(MeetingTodoSaveDto dto) {
        if (dto.getMeetingId() == null || dto.getMeetingId().isBlank()) {
            throw new BusinessException("meetingId 必填");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException("content 必填");
        }
        MeetingTodo row = new MeetingTodo();
        row.setMeetingId(dto.getMeetingId());
        row.setPresetTypeCode(dto.getPresetTypeCode());
        row.setContent(dto.getContent());
        row.setAssigneeId(dto.getAssigneeId());
        row.setAssigneeName(dto.getAssigneeName());
        row.setOperatorId(dto.getOperatorId());
        row.setOperatorName(dto.getOperatorName());
        row.setStatus(dto.getStatus() == null ? "PENDING" : dto.getStatus());
        row.setPriority(dto.getPriority() == null ? "MEDIUM" : dto.getPriority());
        row.setDeadline(dto.getDeadline());
        row.setCompletionNote(dto.getCompletionNote());
        row.setBlockReason(dto.getBlockReason());
        if (dto.getId() != null && !dto.getId().isBlank()) {
            row.setId(dto.getId());
            if ("COMPLETED".equalsIgnoreCase(row.getStatus()) && row.getCompletedAt() == null) {
                row.setCompletedAt(LocalDateTime.now());
            }
            todoMapper.updateById(row);
        } else {
            if (row.getId() == null) {
                row.setId(java.util.UUID.randomUUID().toString());
            }
            row.setRemindCount(0);
            row.setReportedInNext(false);
            todoMapper.insert(row);
        }
        return row.getId();
    }

    @Transactional
    public void forceUpdateStatus(String id, String status, String reason, String completionNote,
                                  String blockReason, String operatorId, String operatorName) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("强制改状态必须填写 reason");
        }
        MeetingTodo row = todoMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("待办不存在: " + id);
        }
        String oldStatus = row.getStatus();
        row.setStatus(status);
        if ("COMPLETED".equalsIgnoreCase(status)) {
            row.setCompletedAt(LocalDateTime.now());
            if (completionNote != null) {
                row.setCompletionNote(completionNote);
            }
        } else {
            row.setCompletedAt(null);
        }
        if (blockReason != null) {
            row.setBlockReason(blockReason);
        }
        todoMapper.updateById(row);

        if ("COMPLETED".equalsIgnoreCase(status) && !"COMPLETED".equalsIgnoreCase(oldStatus)) {
            adjustParticipantCompletedCount(row.getMeetingId(), row.getAssigneeId(), 1);
        } else if (!"COMPLETED".equalsIgnoreCase(status) && "COMPLETED".equalsIgnoreCase(oldStatus)) {
            adjustParticipantCompletedCount(row.getMeetingId(), row.getAssigneeId(), -1);
        }

        MeetingTodoAudit audit = new MeetingTodoAudit();
        audit.setTodoId(id);
        audit.setAction("ADMIN_FORCE_STATUS");
        audit.setOldStatus(oldStatus);
        audit.setNewStatus(status);
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setReason(reason.trim());
        audit.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(audit);
    }

    @Transactional
    public void delete(String id, String reason, String operatorId, String operatorName) {
        MeetingTodo row = todoMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("待办不存在: " + id);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("删除待办必须填写 reason");
        }
        MeetingTodoAudit audit = new MeetingTodoAudit();
        audit.setTodoId(id);
        audit.setAction("ADMIN_DELETE");
        audit.setOldStatus(row.getStatus());
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setReason(reason.trim());
        audit.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(audit);
        todoMapper.deleteById(id);
    }

    public Map<String, Object> stats() {
        List<MeetingTodo> all = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .select(MeetingTodo::getStatus, MeetingTodo::getId));
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("PENDING", 0);
        byStatus.put("IN_PROGRESS", 0);
        byStatus.put("COMPLETED", 0);
        byStatus.put("BLOCKED", 0);
        for (MeetingTodo t : all) {
            String s = t.getStatus() == null ? "PENDING" : t.getStatus();
            byStatus.merge(s, 1, Integer::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("byStatus", byStatus);
        return out;
    }

    private void adjustParticipantCompletedCount(String meetingId, String assigneeId, int delta) {
        if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
            return;
        }
        LambdaQueryWrapper<MeetingParticipant> w = new LambdaQueryWrapper<>();
        w.eq(MeetingParticipant::getMeetingId, meetingId).eq(MeetingParticipant::getUserId, assigneeId);
        MeetingParticipant p = participantMapper.selectOne(w);
        if (p == null) {
            return;
        }
        int cc = p.getCompletedCount() == null ? 0 : p.getCompletedCount();
        p.setCompletedCount(Math.max(0, cc + delta));
        participantMapper.updateById(p);
    }

    private MeetingTodoDto toDto(MeetingTodo row) {
        MeetingTodoDto dto = new MeetingTodoDto();
        dto.setId(row.getId());
        dto.setMeetingId(row.getMeetingId());
        dto.setPresetTypeCode(row.getPresetTypeCode());
        dto.setContent(row.getContent());
        dto.setAssigneeId(row.getAssigneeId());
        dto.setAssigneeName(row.getAssigneeName());
        dto.setOperatorId(row.getOperatorId());
        dto.setOperatorName(row.getOperatorName());
        dto.setStatus(row.getStatus());
        dto.setPriority(row.getPriority());
        dto.setDeadline(row.getDeadline());
        dto.setCompletedAt(row.getCompletedAt());
        dto.setCompletionNote(row.getCompletionNote());
        dto.setBlockReason(row.getBlockReason());
        dto.setLastRemindAt(row.getLastRemindAt());
        dto.setRemindCount(row.getRemindCount());
        dto.setNextMeetingId(row.getNextMeetingId());
        dto.setReportedInNext(row.getReportedInNext());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private MeetingTodoAuditDto toAuditDto(MeetingTodoAudit a) {
        MeetingTodoAuditDto dto = new MeetingTodoAuditDto();
        dto.setId(a.getId());
        dto.setTodoId(a.getTodoId());
        dto.setAction(a.getAction());
        dto.setOldStatus(a.getOldStatus());
        dto.setNewStatus(a.getNewStatus());
        dto.setOperatorId(a.getOperatorId());
        dto.setOperatorName(a.getOperatorName());
        dto.setReason(a.getReason());
        dto.setPayloadJson(a.getPayloadJson());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private MeetingTodoProgressDto toProgressDto(MeetingTodoProgress p) {
        MeetingTodoProgressDto dto = new MeetingTodoProgressDto();
        dto.setId(p.getId());
        dto.setTodoId(p.getTodoId());
        dto.setAuthorId(p.getAuthorId());
        dto.setAuthorName(p.getAuthorName());
        dto.setAuthorRole(p.getAuthorRole());
        dto.setProgressText(p.getProgressText());
        dto.setProgressPercent(p.getProgressPercent());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }

    private MeetingTodoAttachmentDto toAttachmentDto(MeetingTodoAttachment a) {
        MeetingTodoAttachmentDto dto = new MeetingTodoAttachmentDto();
        dto.setId(a.getId());
        dto.setTodoId(a.getTodoId());
        dto.setProgressId(a.getProgressId());
        dto.setUploaderId(a.getUploaderId());
        dto.setUploaderName(a.getUploaderName());
        dto.setFileName(a.getFileName());
        dto.setFileSize(a.getFileSize());
        dto.setMimeType(a.getMimeType());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
