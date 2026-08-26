package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.AsyncUploadService;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncUploadServiceImpl implements AsyncUploadService {

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;
    private final ProgressTracker progressTracker;

    @Override
    @Async("uploadTaskExecutor")
    public void execute(String taskId, long userId, String originalFilename,
            byte[] fileBytes, long fileSize, String contentType) {
        progressTracker.update(taskId, 10, "uploading", "文件读取完成");

        String fileHash = computeSha256(fileBytes);
        progressTracker.update(taskId, 20, "uploading", "SHA-256计算完成");

        File existingFile = fileMapper.selectByFileHash(fileHash);
        if (existingFile != null) {
            saveFileRecord(originalFilename, existingFile.getStoredFilename(),
                    fileSize, contentType, fileHash, userId, existingFile.getOssUrl());
            progressTracker.update(taskId, 100, "done", "秒传成功");
            log.info("文件秒传成功(复用已有文件): {} -> {}", originalFilename, existingFile.getOssUrl());
            return;
        }

        progressTracker.update(taskId, 25, "uploading", "去重检查完成，开始上传OSS");

        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            String storedFilename = ossUtil.upload(inputStream, originalFilename,
                    bytesWritten -> {
                        int ossPercent = 30 + (int) (bytesWritten * 60 / fileSize);
                        progressTracker.update(taskId, Math.min(ossPercent, 90), "uploading", "OSS上传中");
                    });

            progressTracker.update(taskId, 90, "uploading", "OSS上传完成");

            String ossUrl = ossUtil.getOssUrl(storedFilename);
            saveFileRecord(originalFilename, storedFilename, fileSize,
                    contentType, fileHash, userId, ossUrl);

            progressTracker.update(taskId, 100, "done", "上传成功");
            log.info("文件上传成功: {} -> {}", originalFilename, ossUrl);
        } catch (IOException e) {
            progressTracker.update(taskId, 0, "failed", "OSS上传失败: " + e.getMessage());
            log.error("文件上传失败: {}", originalFilename, e);
        }
    }

    private void saveFileRecord(String originalFilename, String storedFilename,
            long fileSize, String contentType, String fileHash,
            long userId, String ossUrl) {
        File fileRecord = new File();
        fileRecord.setOriginalFilename(originalFilename);
        fileRecord.setStoredFilename(storedFilename);
        fileRecord.setFileSize(fileSize);
        fileRecord.setContentType(contentType);
        fileRecord.setFileHash(fileHash);
        fileRecord.setUserId(userId);
        fileRecord.setOssUrl(ossUrl);
        fileRecord.setUploadTime(LocalDateTime.now());
        fileMapper.insert(fileRecord);
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }
}