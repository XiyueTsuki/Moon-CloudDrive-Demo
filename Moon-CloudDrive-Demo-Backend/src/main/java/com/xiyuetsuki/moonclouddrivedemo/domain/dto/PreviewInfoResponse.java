package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件预览信息响应
 * <p>
 * 前端根据 previewType 选择不同的渲染组件：
 * <pre>
 *   image       → img / v-viewer 图片查看器
 *   video       → video / DPlayer
 *   audio       → audio
 *   pdf         → iframe + pdf.js
 *   text        → 前端再调 /api/file/preview/text 获取文本 → highlight.js / Monaco
 *   unsupported → 提示下载
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewInfoResponse {

    /** 预览类型: image / video / audio / pdf / text / unsupported */
    private String previewType;

    /** 预览 URL（图片/视频/音频/PDF 时为 OSS 预签名或处理 URL，text 时为 null） */
    private String previewUrl;

    /** 原始文件名 */
    private String fileName;

    /** MIME 类型（如 image/png, video/mp4），用于浏览器 Content-Type 协商 */
    private String mimeType;

    /** 文件大小（字节），前端可根据大小判断是否需要加载提示 */
    private Long fileSize;

    /** 文本文件语言标识（仅 previewType=text 时有效），如 "java", "json", "md" */
    private String language;
}