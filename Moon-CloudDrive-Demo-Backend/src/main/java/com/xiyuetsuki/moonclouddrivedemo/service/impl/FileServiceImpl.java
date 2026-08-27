package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.AsyncUploadService;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件服务实现类，负责文件上传、查询、删除、重命名、下载等核心业务逻辑
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final AsyncUploadService asyncUploadService;
    private final ProgressTracker progressTracker;
    private final FileMapper fileMapper;
    private final OssUtil ossUtil;

    @Override
    public String uploadFile(MultipartFile file) {
        // 生成唯一任务ID，用于追踪上传进度
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long userId = StpUtil.getLoginIdAsLong();
        String originalFilename = file.getOriginalFilename();

        // 初始化进度为 0%
        progressTracker.update(taskId, 0, "uploading", "开始上传");

        // 将文件内容读取为字节数组，以便异步处理
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            // 文件读取失败，标记任务失败
            progressTracker.update(taskId, 0, "failed", "文件读取失败: " + e.getMessage());
            throw new RuntimeException("文件读取失败", e);
        }

        // 提交异步上传任务
        asyncUploadService.execute(taskId, userId, originalFilename,
                fileBytes, file.getSize(), file.getContentType());

        return taskId;
    }

    @Override
    public List<FileVO> listFiles() {
        // 获取当前登录用户ID
        long userId = StpUtil.getLoginIdAsLong();
        // 查询该用户的所有文件，按上传时间倒序排列
        List<File> files = fileMapper.selectByUserId(userId);
        // 将实体转换为视图对象，隐藏敏感字段（如存储路径、OSS URL等）
        return files.stream().map(this::toFileVO).collect(Collectors.toList());
    }

    @Override
    public void deleteFile(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        // 查询文件记录，确保文件存在且属于当前用户
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权操作");
        }
        // 删除数据库记录
        fileMapper.deleteById(fileId);
        log.info("文件删除成功: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
    }

    @Override
    public void renameFile(Long fileId, String newName) {
        // 校验新文件名不能为空
        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        long userId = StpUtil.getLoginIdAsLong();
        // 查询文件记录，确保文件存在且属于当前用户
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权操作");
        }
        // 更新文件名为新名称
        file.setOriginalFilename(newName.trim());
        fileMapper.updateById(file);
        log.info("文件重命名成功: userId={}, fileId={}, oldName={}, newName={}",
                userId, fileId, file.getOriginalFilename(), newName);
    }

    @Override
    public String getDownloadUrl(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        // 查询文件记录，确保文件存在且属于当前用户
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权操作");
        }
        // 生成OSS预签名URL，有效期1小时，支持浏览器直接下载
        String presignedUrl = ossUtil.generatePresignedUrl(
                file.getStoredFilename(), file.getOriginalFilename());
        log.info("生成下载链接: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
        return presignedUrl;
    }

    /**
     * 将 File 实体转换为 FileVO 视图对象，隐藏内部存储细节
     *
     * @param file 文件实体
     * @return 文件视图对象
     */
    private FileVO toFileVO(File file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setOriginalFilename(file.getOriginalFilename());
        vo.setFileSize(file.getFileSize());
        vo.setContentType(file.getContentType());
        vo.setFileHash(file.getFileHash());
        vo.setUploadTime(file.getUploadTime());
        return vo;
    }
}