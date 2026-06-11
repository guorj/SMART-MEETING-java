package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片代理引用：飞书 image_key 或本地文件页/幻灯片通过后端代理访问。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageRefDto {
    /** 唯一标识，用于代理 URL 拼接 */
    private String id;
    /** 代理访问 URL，如 /api/v1/meetings/{id}/agenda-materials/proxy-image?imageKey=xxx */
    private String proxyUrl;
    /** 图片原始宽度（px），可为 0 表示未知 */
    private int width;
    /** 图片原始高度（px），可为 0 表示未知 */
    private int height;
    /** 替代文本 */
    private String alt;
}
