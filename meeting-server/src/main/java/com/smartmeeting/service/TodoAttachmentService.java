package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.TodoAttachmentResponse;
import com.smartmeeting.config.MeetingTodoProperties;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.MeetingTodoAttachment;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.TodoAttachmentMapper;
import com.smartmeeting.repository.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoAttachmentService {

    private final TodoMapper todoMapper;
    private final TodoAttachmentMapper attachmentMapper;
    private final TodoPermissionService todoPermissionService;
    private final MeetingTodoProperties todoProperties;

    public List<TodoAttachmentResponse> listAttachments(String todoId, String feishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssigneeOrOperator(todo, feishuUserId);
        LambdaQueryWrapper<MeetingTodoAttachment> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodoAttachment::getTodoId, todoId)
                .orderByAsc(MeetingTodoAttachment::getCreatedAt);
        return attachmentMapper.selectList(w).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TodoAttachmentResponse upload(String todoId, MultipartFile file, String progressId,
                                         String feishuUserId, String userName) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssigneeOrOperator(todo, feishuUserId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > todoProperties.getAttachmentMaxBytes()) {
            throw new BusinessException(400, "文件超过大小限制: " + todoProperties.getAttachmentMaxBytes() + " 字节");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = extension(originalName);
        if (!todoProperties.getAttachmentAllowedExtensions().contains(ext)) {
            throw new BusinessException(400, "不允许的文件类型: " + ext);
        }

        Path baseDir = Paths.get(todoProperties.getAttachmentDir()).toAbsolutePath().normalize();
        Path todoDir = baseDir.resolve(todoId);
        try {
            Files.createDirectories(todoDir);
        } catch (IOException e) {
            throw new BusinessException(500, "无法创建附件目录");
        }

        String storedName = UUID.randomUUID().toString() + (ext.isEmpty() ? "" : "." + ext);
        Path target = todoDir.resolve(storedName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }

        String relativePath = todoId + "/" + storedName;
        MeetingTodoAttachment row = new MeetingTodoAttachment();
        row.setId(UUID.randomUUID().toString());
        row.setTodoId(todoId);
        row.setProgressId(progressId);
        row.setUploaderId(feishuUserId);
        row.setUploaderName(userName);
        row.setFileName(originalName);
        row.setStoragePath(relativePath);
        row.setFileSize(file.getSize());
        row.setMimeType(file.getContentType());
        row.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(row);
        return toResponse(row);
    }

    public Resource loadForDownload(String todoId, String attachmentId, String feishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssigneeOrOperator(todo, feishuUserId);
        MeetingTodoAttachment row = attachmentMapper.selectById(attachmentId);
        if (row == null || !todoId.equals(row.getTodoId())) {
            throw new BusinessException(404, "附件不存在");
        }
        Path path = resolveStoragePath(row.getStoragePath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    public MeetingTodoAttachment requireAttachment(String attachmentId) {
        MeetingTodoAttachment row = attachmentMapper.selectById(attachmentId);
        if (row == null) {
            throw new BusinessException(404, "附件不存在");
        }
        return row;
    }

    private Path resolveStoragePath(String storagePath) {
        Path base = Paths.get(todoProperties.getAttachmentDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(storagePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException(400, "非法附件路径");
        }
        return resolved;
    }

    private MeetingTodo requireTodo(String todoId) {
        MeetingTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在: " + todoId);
        }
        return todo;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private TodoAttachmentResponse toResponse(MeetingTodoAttachment a) {
        return TodoAttachmentResponse.builder()
                .id(a.getId())
                .todoId(a.getTodoId())
                .progressId(a.getProgressId())
                .uploaderId(a.getUploaderId())
                .uploaderName(a.getUploaderName())
                .fileName(a.getFileName())
                .fileSize(a.getFileSize())
                .mimeType(a.getMimeType())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
