package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PDF 服务端转图片预览响应
 * <p>
 * 后端将 PDF 每页转为 PNG 存入 OSS，前端按页加载图片展示。
 * 首次访问触发异步转换，status=converting 时前端轮询等待。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfPreviewResponse {

    /** PDF 总页数（status=ready 时有效） */
    private int totalPages;

    /** 转换状态: converting / ready / failed */
    private String status;

    /**
     * 每页图片的 OSS 预签名 URL 列表（下标 = 页码-1）
     * 仅在 status=ready 时有效
     */
    private List<String> pageUrls;

    /** 错误信息（status=failed 时有效） */
    private String errorMessage;
}