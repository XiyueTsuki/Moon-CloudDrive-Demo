package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PdfPreviewResponse;

/**
 * PDF 转图片服务
 * <p>
 * 将 PDF 每一页渲染为 PNG 图片，上传至 OSS 专用目录缓存。
 * 首次访问触发异步转换，后续直接返回已缓存的图片 URL。
 */
public interface PdfConvertService {

    /**
     * 获取 PDF 预览信息（包含总页数 + 所有页图片 URL）
     * <p>
     * 如果尚未转换，会异步触发转换任务，
     * 前端通过 status 字段判断是否需要轮询等待。
     *
     * @param fileId 文件 ID
     * @return PDF 预览信息
     */
    PdfPreviewResponse getPdfPreview(Long fileId);

    /**
     * 获取单页图片的字节流
     * <p>
     * 从 OSS 的 pdf-preview 目录读取已转换的页面图片。
     * 如果该页尚未转换，将抛出异常。
     *
     * @param fileId  文件 ID
     * @param pageNum 页码（从 1 开始）
     * @return 图片字节数组
     */
    byte[] getPageImage(Long fileId, int pageNum);

    /**
     * 清理指定文件的转换缓存（删除文件时调用）
     *
     * @param fileId 文件 ID
     */
    void clearConvertCache(Long fileId);
}