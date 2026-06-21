package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会议待办提取与同步配置，绑定 {@code meeting.todo.*} 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.todo")
public class MeetingTodoProperties {

    /** 会后是否执行待办提取与同步链路 */
    private boolean extractionEnabled = false;

    /** 附件本地存储目录（相对或绝对路径） */
    private String attachmentDir = "data/todo-attachments";

    /** 单文件最大字节数，默认 10MB */
    private long attachmentMaxBytes = 10 * 1024 * 1024;

    /** 允许上传的扩展名（小写，不含点） */
    private List<String> attachmentAllowedExtensions = List.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "gif", "webp", "txt", "zip", "rar"
    );
}

