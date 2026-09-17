package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本文件预览内容响应
 * <p>
 * 前端通过 GET /api/file/preview/text?fileId=xxx 获取文本内容，
 * 使用 highlight.js / Monaco Editor 按 language 标识进行代码高亮渲染
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextPreviewResponse {

    /** 文本文件完整内容（UTF-8 解码后），最大 1MB */
    private String content;

    /** 语言标识，供前端 highlight.js / Monaco 使用，如 "java", "json", "xml", "python" */
    private String language;

    /** 字符编码，固定为 "UTF-8" */
    private String encoding;
}