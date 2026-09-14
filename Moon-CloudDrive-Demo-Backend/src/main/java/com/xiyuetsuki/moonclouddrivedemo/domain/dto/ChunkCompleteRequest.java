package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

/**
 * 完成分片上传请求参数
 * 所有分片上传完毕后调用接口合并OSS中的碎片并创建文件记录
 */
@Data
public class ChunkCompleteRequest {

    /** 上传任务唯一标识 */
    private String uploadId;

    /** 文件MIME类型，如 application/octet-stream */
    private String contentType;
}