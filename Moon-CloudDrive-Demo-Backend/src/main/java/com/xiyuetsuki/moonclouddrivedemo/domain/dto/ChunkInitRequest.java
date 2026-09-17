package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

/**
 * 分片上传初始化请求参数
 * 前端在开始大文件上传前，先调用此接口上传文件基本信息并获取分片参数
 */
@Data
public class ChunkInitRequest {

    /** 原始文件名 */
    private String fileName;

    /** 文件总大小（字节） */
    private Long fileSize;

    /** 文件SHA-256哈希值，用于秒传去重 */
    private String fileHash;

    /** 上传到的目标文件夹ID，null表示根目录 */
    private Long parentId;

    /** 文件MIME类型，如 image/png，用于OSS对象元数据 */
    private String contentType;
}