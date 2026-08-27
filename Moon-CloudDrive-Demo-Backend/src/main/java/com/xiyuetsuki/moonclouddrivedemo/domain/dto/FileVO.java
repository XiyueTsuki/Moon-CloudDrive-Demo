package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息视图对象，用于前端展示文件列表
 */
@Data
public class FileVO {

    /** 文件记录ID */
    private Long id;

    /** 原始文件名 */
    private String originalFilename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件MIME类型 */
    private String contentType;

    /** 文件SHA-256哈希值 */
    private String fileHash;

    /** 上传时间 */
    private LocalDateTime uploadTime;
}