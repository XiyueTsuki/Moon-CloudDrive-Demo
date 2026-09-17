package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PreviewInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.TextPreviewResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件预览服务
 * <p>
 * 根据不同文件类型返回对应的预览信息：
 * <ul>
 *   <li>图片 → OSS 图片处理 URL（缩放 + 水印）</li>
 *   <li>视频/音频 → OSS 预签名 URL（浏览器原生播放）</li>
 *   <li>PDF → OSS 预签名 URL（前端 pdf.js 渲染）</li>
 *   <li>文本/代码 → 从 OSS 读取内容，前端高亮展示</li>
 *   <li>其他 → 返回不支持预览</li>
 * </ul>
 */
public interface PreviewService {

    /**
     * 获取文件预览信息，根据文件类型返回预览 URL 或 text/unsupported 标识
     *
     * @param fileId 文件 ID
     * @return 预览信息，包含类型、URL、大小等
     */
    PreviewInfoResponse getPreviewInfo(Long fileId);

    /**
     * 获取文本文件内容，用于前端代码高亮预览
     *
     * @param fileId 文件 ID
     * @return 文本内容 + 语言标识
     */
    TextPreviewResponse getTextContent(Long fileId);

    /**
     * 流式代理 OSS 文件内容到 HTTP 响应（安全模式）
     * <p>
     * 不暴露 OSS URL，由服务端中转向浏览器输出文件流。
     * 适用于图片 / 视频 / 音频 / PDF 的流式预览
     *
     * @param fileId   文件 ID
     * @param response HTTP 响应对象
     */
    void previewStream(Long fileId, HttpServletResponse response);
}