package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.domain.message.PackDownloadMessage;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.PackDownloadService;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PackDownloadServiceImpl implements PackDownloadService {

    private final FileMapper fileMapper;
    private final ProgressTracker progressTracker;
    private final DefaultMQProducer packDownloadProducer;
    private final ObjectMapper objectMapper;

    @Value("${moon.pack.max-file-count}")
    private int maxFileCount;

    @Value("${moon.pack.max-total-size}")
    private long maxTotalSize;

    @Override
    public String preparePack(List<Long> fileIds) {
        long userId = StpUtil.getLoginIdAsLong();

        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException("请至少选择一个文件");
        }
        if (fileIds.size() > maxFileCount) {
            throw new BusinessException("单次最多打包 " + maxFileCount + " 个文件");
        }

        long totalSize = 0;
        for (Long fileId : fileIds) {
            File file = fileMapper.selectByUserIdAndId(userId, fileId);
            if (file == null) {
                throw new BusinessException("文件不存在或无权操作: fileId=" + fileId);
            }
            if (file.getIsFolder() != null && file.getIsFolder() == 1) {
                throw new BusinessException("暂不支持打包文件夹，请选择文件: " + file.getOriginalFilename());
            }
            if (file.getStoredFilename() == null || file.getStoredFilename().isEmpty()) {
                throw new BusinessException("文件存储信息异常: " + file.getOriginalFilename());
            }
            totalSize += file.getFileSize() != null ? file.getFileSize() : 0;
        }

        if (totalSize > maxTotalSize) {
            throw new BusinessException("打包文件总大小超过 " + (maxTotalSize / 1024 / 1024) + "MB 限制");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        progressTracker.updatePackProgress(taskId, PackProgressResponse.queued());

        PackDownloadMessage msgBody = new PackDownloadMessage(taskId, userId, fileIds);
        try {
            byte[] body = objectMapper.writeValueAsBytes(msgBody);
            Message message = new Message("PACK_DOWNLOAD_TOPIC", "prepare", body);
            packDownloadProducer.send(message);
        } catch (Exception e) {
            log.error("发送打包消息失败: taskId={}", taskId, e);
            progressTracker.updatePackProgress(taskId,
                    PackProgressResponse.failed("系统繁忙，请稍后重试"));
            throw new BusinessException("提交打包任务失败，请稍后重试");
        }

        log.info("打包任务已提交: taskId={}, fileCount={}, totalSize={}",
                taskId, fileIds.size(), totalSize);
        return taskId;
    }

    @Override
    public PackProgressResponse getPackProgress(String taskId) {
        return progressTracker.getPackProgress(taskId);
    }

    @Override
    public String getPackFilePath(String taskId) {
        PackProgressResponse progress = progressTracker.getPackProgress(taskId);
        if (progress == null || !"ready".equals(progress.getStatus())) {
            return null;
        }
        return getTempZipPath(taskId);
    }

    public static String getTempZipPath(String taskId) {
        return System.getProperty("java.io.tmpdir") + "/moon-pack/" + taskId + ".zip";
    }
}