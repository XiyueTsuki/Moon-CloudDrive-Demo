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

    /** 软删除标记：0-正常，1-已删除（回收站中） */
    private Integer deleted;

    /** 进入回收站的时间 */
    private LocalDateTime deleteTime;

    /** 父文件夹ID，NULL表示根目录 */
    private Long parentId;

    /** 是否为文件夹：0-文件，1-文件夹 */
    private Integer isFolder;
}