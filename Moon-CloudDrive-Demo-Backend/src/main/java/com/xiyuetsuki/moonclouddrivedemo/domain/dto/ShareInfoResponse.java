package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分享文件/文件夹的元信息响应（访问分享页面时使用）
 */
@Data
@AllArgsConstructor
public class ShareInfoResponse {

    /** 分享码 */
    private String shareCode;

    /** 文件/文件夹名称 */
    private String fileName;

    /** 文件大小（字节），文件夹时为0 */
    private Long fileSize;

    /** 是否需要提取码 */
    private boolean needPassword;

    /** 是否为文件夹（使用 JsonProperty 显式指定属性名，避免 Jackson 将 isXxx 解析为 xxx） */
    @JsonProperty("isFolder")
    private boolean isFolder;

    /** 文件下载链接（仅单个文件分享时返回，文件夹时为null） */
    private String downloadUrl;
}