package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.service.AsyncUploadService;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final AsyncUploadService asyncUploadService;
    private final ProgressTracker progressTracker;

    @Override
    public String uploadFile(MultipartFile file) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long userId = StpUtil.getLoginIdAsLong();
        String originalFilename = file.getOriginalFilename();

        progressTracker.update(taskId, 0, "uploading", "开始上传");

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            progressTracker.update(taskId, 0, "failed", "文件读取失败: " + e.getMessage());
            throw new RuntimeException("文件读取失败", e);
        }

        asyncUploadService.execute(taskId, userId, originalFilename,
                fileBytes, file.getSize(), file.getContentType());

        return taskId;
    }
}