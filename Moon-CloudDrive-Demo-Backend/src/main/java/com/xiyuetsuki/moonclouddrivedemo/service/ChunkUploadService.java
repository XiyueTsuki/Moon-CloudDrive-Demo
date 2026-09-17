package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkInitResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传服务接口
 * 用于处理超大文件的分片上传和断点续传，将文件按固定大小分片后逐片上传至阿里云OSS
 */
public interface ChunkUploadService {

    /**
     * 初始化分片上传任务
     * 校验目标文件夹存在性、检查文件哈希是否可秒传，
     * 计算分片数量并初始化OSS的多段上传任务
     *
     * @param fileName 原始文件名
     * @param fileSize 文件总大小（字节）
     * @param fileHash 文件SHA-256哈希值
     * @param parentId 目标文件夹ID，null表示根目录
     * @return 包含上传任务标识和分片参数的响应（秒传时返回已有文件信息）
     */
    ChunkInitResponse initChunkUpload(String fileName, long fileSize,
                                      String fileHash, Long parentId, String contentType);

    /**
     * 上传单个分片至OSS
     * 分片序号从0开始，后端自动转换为OSS的partNumber（从1开始）
     *
     * @param uploadId   上传任务标识
     * @param chunkIndex 分片序号（从0开始）
     * @param chunk      分片数据
     */
    void uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk);

    /**
     * 完成分片上传
     * 检查所有分片是否齐全后合并OSS中的碎片，创建数据库文件记录并清理Redis缓存
     *
     * @param uploadId    上传任务标识
     * @param contentType 文件MIME类型
     * @return 创建的文件记录视图
     */
    FileVO completeChunkUpload(String uploadId, String contentType);

    /**
     * 查询分片上传进度
     * 用于前端断点续传时获取已上传的分片列表
     *
     * @param uploadId 上传任务标识
     * @return 包含已完成分片集合和百分比的进度信息
     */
    ChunkProgressResponse getChunkProgress(String uploadId);

    /**
     * 取消分片上传
     * 中止OSS端的多段上传任务并清理服务器Redis缓存
     *
     * @param uploadId 上传任务标识
     */
    void abortChunkUpload(String uploadId);
}