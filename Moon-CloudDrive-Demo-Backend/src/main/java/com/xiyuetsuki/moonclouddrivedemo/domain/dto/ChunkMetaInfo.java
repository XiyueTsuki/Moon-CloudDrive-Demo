package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传元信息
 * 存储在Redis中，记录一个分片上传任务的完整上下文（24小时有效期）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkMetaInfo {

    /** 原始文件名 */
    private String fileName;

    /** 文件总大小（字节） */
    private long fileSize;

    /** 文件SHA-256哈希值 */
    private String fileHash;

    /** 上传用户ID */
    private long userId;

    /** 目标父文件夹ID，null表示根目录 */
    private Long parentId;

    /** 总的分片数量 */
    private int chunkCount;

    /** 每个分片的大小（字节） */
    private long chunkSize;

    /** OSS返回的分片上传标识 */
    private String ossUploadId;

    /** 文件在OSS中的存储名称（UUID + 扩展名） */
    private String storedFilename;

    /** 上传状态：uploading / completed / aborted */
    private String status;
}