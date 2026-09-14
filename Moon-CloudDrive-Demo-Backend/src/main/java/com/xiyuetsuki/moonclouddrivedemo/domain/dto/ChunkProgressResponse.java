package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 分片上传进度响应
 * 用于断点续传时查询已上传分片，客户端根据此信息跳过已完成分片继续上传
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkProgressResponse {

    /** 本次上传任务的唯一标识 */
    private String uploadId;

    /** 总的分片数量 */
    private int chunkCount;

    /** 已完成的分片数量 */
    private int completedCount;

    /** 已完成的分片序号集合（chunkIndex + 1，即从1开始） */
    private Set<Integer> completedParts;

    /** 完成百分比（0-100） */
    private int percent;
}