package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aliyun.oss.model.PartETag;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkInitResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkMetaInfo;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.ChunkUploadService;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 分片上传服务实现
 *
 * 核心流程：
 * 1. init   → 校验条件、检查秒传、创建OSS多段上传任务
 * 2. upload → 逐片将客户端分片流式传输到OSS
 * 3. complete → 校验分片完整性、合并OSS碎片、写入数据库
 * 4. getProgress → 查询Redis中的分片完成情况，用于断点续传
 * 5. abort → 取消OSS多段上传并清理Redis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkUploadServiceImpl implements ChunkUploadService {

    /** 默认分片大小：5MB */
    private static final long DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024L;

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;
    private final ProgressTracker progressTracker;

    @Value("${moon.chunk.upload.chunk-size:" + DEFAULT_CHUNK_SIZE + "}")
    private long chunkSize;

    @Override
    public ChunkInitResponse initChunkUpload(String fileName, long fileSize,
                                              String fileHash, Long parentId, String contentType) {

        /*
        分片上传初始化

        检查目标文件夹是否存在 ->
        (秒传)复用已有文件记录
        (分片上传)计算分片数量 -> 创建元信息实体存入Redis -> 返回Response
         */

        long userId = StpUtil.getLoginIdAsLong();

        // 校验目标文件夹是否存在
        if (parentId != null) {
            File parentFolder = fileMapper.selectByUserIdAndId(userId, parentId);
            if (parentFolder == null || parentFolder.getIsFolder() == null
                    || parentFolder.getIsFolder() != 1) {
                throw new BusinessException("目标文件夹不存在或不是有效文件夹");
            }
        }

        // 秒传检查：相同哈希的文件已存在则直接复用OSS文件创建新记录
        File existingFile = fileMapper.selectByFileHash(fileHash);
        if (existingFile != null) {
            log.info("文件秒传(分片): {} -> {}", fileName, existingFile.getOssUrl());

            /*
             * 秒传逻辑：
             * 文件内容已存在于OSS中，无需重复上传
             * 但需为当前用户在数据库中创建独立的文件记录
             * 新记录指向同一OSS文件，拥有独立的文件名、所属文件夹等属性
             */
            File newFile = new File();
            newFile.setOriginalFilename(fileName);
            newFile.setStoredFilename(existingFile.getStoredFilename());
            newFile.setFileSize(existingFile.getFileSize());
            newFile.setContentType(existingFile.getContentType());
            newFile.setFileHash(fileHash);
            newFile.setUserId(userId);
            newFile.setOssUrl(existingFile.getOssUrl());
            newFile.setUploadTime(LocalDateTime.now());
            newFile.setParentId(parentId);
            newFile.setIsFolder(0);
            fileMapper.insert(newFile);

            FileVO fileVO = buildFileVO(newFile);
            return ChunkInitResponse.instant(fileVO);
        }

        // 计算分片数量并初始化OSS多段上传
        int chunkCount = (int) Math.ceil((double) fileSize / chunkSize);
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String storedFilename = ossUtil.generateStoredFilename(fileName);
        String ossUploadId = ossUtil.initiateMultipartUpload(storedFilename, contentType);

        // 保存上传元信息到Redis（24小时有效期），用于后续分片上传和断点续传
        ChunkMetaInfo meta = new ChunkMetaInfo();
        meta.setFileName(fileName);
        meta.setFileSize(fileSize);
        meta.setFileHash(fileHash);
        meta.setUserId(userId);
        meta.setParentId(parentId);
        meta.setContentType(contentType);
        meta.setChunkCount(chunkCount);
        meta.setChunkSize(chunkSize);
        meta.setOssUploadId(ossUploadId);
        meta.setStoredFilename(storedFilename);
        meta.setStatus("uploading");

        progressTracker.initChunkMeta(uploadId, meta);

        log.info("分片上传初始化: uploadId={}, ossUploadId={}, fileName={}, fileSize={}, chunkCount={}",
                uploadId, ossUploadId, fileName, fileSize, chunkCount);

        return new ChunkInitResponse(uploadId, chunkCount, chunkSize, false, null);
    }

    @Override
    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk) {

        /*
        上传单个分片

        从Redis读取分片上传任务元信息 -> 校验分片序号合法性 -> 流式上传至OSS -> 记录分片上传状态
         */

        // 校验上传任务存在性
        ChunkMetaInfo meta = progressTracker.getChunkMeta(uploadId);
        if (meta == null) {
            throw new BusinessException("上传任务不存在或已过期");
        }

        // 客户端chunkIndex从0开始，OSS的partNumber从1开始
        int partNumber = chunkIndex + 1;
        if (partNumber < 1 || partNumber > meta.getChunkCount()) {
            throw new BusinessException("分片序号超出范围: " + partNumber);
        }

        // 通过流直接传输分片到OSS，不落盘
        try (InputStream inputStream = chunk.getInputStream()) {
            PartETag partETag = ossUtil.uploadPart(meta.getStoredFilename(),
                    meta.getOssUploadId(), partNumber, inputStream, chunk.getSize());
            // 记录分片完成状态和ETag（用于最终合并时校验完整性）
            progressTracker.markChunkComplete(uploadId, partNumber, partETag.getETag());
            log.debug("分片上传完成: uploadId={}, part={}/{}, etag={}",
                    uploadId, partNumber, meta.getChunkCount(), partETag.getETag());
        } catch (IOException e) {
            log.error("分片上传失败: uploadId={}, part={}", uploadId, partNumber, e);
            throw new RuntimeException("分片上传失败", e);
        }
    }

    @Override
    public FileVO completeChunkUpload(String uploadId, String contentType) {

        /*
        分片上传合并

        从Redis读取分片上传任务元信息 -> 校验所有分片是否上传完毕 ->
        合并OSS中的分片为完整文件 -> 创建数据库文件记录 -> 清理Redis中的文件分片数据
         */

        // 获取上传元信息并校验任务存在
        ChunkMetaInfo meta = progressTracker.getChunkMeta(uploadId);
        if (meta == null) {
            throw new BusinessException("上传任务不存在或已过期");
        }

        // 校验所有分片是否已上传完毕
        List<PartETag> partETags = progressTracker.getPartETags(uploadId);
        if (partETags.size() != meta.getChunkCount()) {
            throw new BusinessException(String.format(
                    "分片未全部上传完成: 已完成 %d/%d", partETags.size(), meta.getChunkCount()));
        }

        // 合并OSS中的碎片为完整文件
        ossUtil.completeMultipartUpload(meta.getStoredFilename(),
                meta.getOssUploadId(), partETags);

        // 创建数据库文件记录
        String ossUrl = ossUtil.getOssUrl(meta.getStoredFilename());
        File fileRecord = new File();
        fileRecord.setOriginalFilename(meta.getFileName());
        fileRecord.setStoredFilename(meta.getStoredFilename());
        fileRecord.setFileSize(meta.getFileSize());
        fileRecord.setContentType(contentType);
        fileRecord.setFileHash(meta.getFileHash());
        fileRecord.setUserId(meta.getUserId());
        fileRecord.setOssUrl(ossUrl);
        fileRecord.setUploadTime(LocalDateTime.now());
        fileRecord.setParentId(meta.getParentId());
        fileMapper.insert(fileRecord);

        // 清理Redis中的分片数据
        progressTracker.cleanupChunk(uploadId);

        log.info("分片上传完成: uploadId={}, fileName={}, ossUrl={}", uploadId,
                meta.getFileName(), ossUrl);

        return buildFileVO(fileRecord);
    }

    @Override
    public ChunkProgressResponse getChunkProgress(String uploadId) {

        /*
        查询分片上传进度

        利用进度追踪类计算上传百分比
         */

        ChunkProgressResponse progress = progressTracker.getChunkProgress(uploadId);
        if (progress == null) {
            throw new BusinessException("上传任务不存在或已过期");
        }
        return progress;
    }

    @Override
    public void abortChunkUpload(String uploadId) {

        /*
        取消分片上传

        中止OSS分片上传 -> 清理Redis缓存
         */

        ChunkMetaInfo meta = progressTracker.getChunkMeta(uploadId);
        if (meta != null) {
            /*
             * 中止OSS端的多段上传（释放未合并的碎片）
             * 此操作属于 best-effort：如果OSS端任务已完成/已中止（StaleUpload），
             * 忽略异常即可——只需确保Redis缓存被清理
             */
            try {
                ossUtil.abortMultipartUpload(meta.getStoredFilename(), meta.getOssUploadId());
            } catch (RuntimeException e) {
                log.warn("中止OSS分片上传时发生异常（可能任务已自动清理）: uploadId={}, ossUploadId={}, error={}",
                        uploadId, meta.getOssUploadId(), e.getMessage());
            }
            // 清理Redis缓存
            progressTracker.cleanupChunk(uploadId);
            log.info("分片上传已取消: uploadId={}, fileName={}", uploadId, meta.getFileName());
        }
    }

    /**
     * 将数据库文件实体转换为前端展示视图
     */
    private FileVO buildFileVO(File file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setOriginalFilename(file.getOriginalFilename());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setFileHash(file.getFileHash());
        vo.setUploadTime(file.getUploadTime());
        vo.setParentId(file.getParentId());
        vo.setIsFolder(file.getIsFolder());
        return vo;
    }
}