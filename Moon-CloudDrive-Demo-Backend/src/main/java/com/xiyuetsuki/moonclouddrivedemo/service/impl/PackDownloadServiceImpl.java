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
import java.util.ArrayList;
import java.util.LinkedHashSet;
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

        // 展开文件夹：对于选中的文件夹，递归收集其下所有子孙文件
        // 使用 LinkedHashSet 去重，同一文件可能被选入多个文件夹或多次选中
        LinkedHashSet<Long> expandedFileIds = new LinkedHashSet<>();
        long totalSize = 0;

        for (Long fileId : fileIds) {
            File file = fileMapper.selectByUserIdAndId(userId, fileId);
            if (file == null) {
                throw new BusinessException("文件不存在或无权操作: fileId=" + fileId);
            }

            if (file.getIsFolder() != null && file.getIsFolder() == 1) {
                // 文件夹：递归收集所有子孙文件
                List<File> descendants = fileMapper.selectAllDescendants(file.getId());
                for (File f : descendants) {
                    // 跳过子文件夹本身（文件夹没有 storedFilename，无法从 OSS 下载）
                    if (f.getIsFolder() != null && f.getIsFolder() == 1) {
                        continue;
                    }
                    if (f.getStoredFilename() == null || f.getStoredFilename().isEmpty()) {
                        log.warn("打包时跳过存储信息异常的文件: fileId={}, name={}",
                                f.getId(), f.getOriginalFilename());
                        continue;
                    }
                    if (expandedFileIds.add(f.getId())) {
                        totalSize += f.getFileSize() != null ? f.getFileSize() : 0;
                    }
                }
            } else {
                // 普通文件：直接加入
                if (file.getStoredFilename() == null || file.getStoredFilename().isEmpty()) {
                    throw new BusinessException("文件存储信息异常: " + file.getOriginalFilename());
                }
                if (expandedFileIds.add(file.getId())) {
                    totalSize += file.getFileSize() != null ? file.getFileSize() : 0;
                }
            }
        }

        if (expandedFileIds.isEmpty()) {
            throw new BusinessException("选中的文件/文件夹中没有可下载的文件");
        }
        if (expandedFileIds.size() > maxFileCount) {
            throw new BusinessException("展开后文件数 " + expandedFileIds.size()
                    + " 超过单次打包上限 " + maxFileCount);
        }
        if (totalSize > maxTotalSize) {
            throw new BusinessException("打包文件总大小超过 "
                    + (maxTotalSize / 1024 / 1024) + "MB 限制");
        }

        List<Long> expandedFileIdList = new ArrayList<>(expandedFileIds);
        log.info("打包任务展开: 用户选中 {} 个节点 → 展开为 {} 个实际文件, 总大小 {} bytes",
                fileIds.size(), expandedFileIdList.size(), totalSize);

        return submitPackTask(userId, expandedFileIdList);
    }

    @Override
    public String submitPackTask(Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException("没有可下载的文件");
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

        log.info("打包任务已提交: taskId={}, userId={}, fileCount={}",
                taskId, userId, fileIds.size());
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