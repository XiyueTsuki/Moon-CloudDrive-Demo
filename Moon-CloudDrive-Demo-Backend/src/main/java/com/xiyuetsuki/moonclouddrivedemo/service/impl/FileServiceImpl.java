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
import java.time.LocalDateTime;
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
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权操作");
        }
        // 软删除：设置删除标记和删除时间，文件进入回收站
        file.setDeleted(1);
        file.setDeleteTime(LocalDateTime.now());
        fileMapper.updateById(file);
        log.info("文件已移入回收站: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
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

    // ==================== 回收站功能 ====================

    @Override
    public List<FileVO> listRecycleBin() {
        long userId = StpUtil.getLoginIdAsLong();
        List<File> files = fileMapper.selectRecycleBinByUserId(userId);
        return files.stream().map(this::toFileVO).collect(Collectors.toList());
    }

    @Override
    public void restoreFile(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("回收站中不存在该文件或无权操作");
        }
        if (file.getDeleted() == null || file.getDeleted() != 1) {
            throw new RuntimeException("该文件不在回收站中");
        }
        // 清除删除标记和删除时间，文件恢复为正常状态
        file.setDeleted(0);
        file.setDeleteTime(null);
        fileMapper.updateById(file);
        log.info("文件已从回收站恢复: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
    }

    @Override
    public void permanentDeleteFile(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权操作");
        }
        if (file.getDeleted() == null || file.getDeleted() != 1) {
            throw new RuntimeException("只能彻底删除回收站中的文件");
        }
        // 物理删除数据库记录
        fileMapper.deleteById(fileId);
        // 同时从 OSS 中删除实际文件
        ossUtil.deleteFile(file.getStoredFilename());
        log.info("文件已彻底删除: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
    }

    @Override
    public void cleanupExpiredRecycleBinFiles() {
        // 查询回收站中超过默认保留天数的文件
        List<File> expiredFiles = fileMapper.selectExpiredRecycleBinFiles(RECYCLE_RETENTION_DAYS);
        if (expiredFiles.isEmpty()) {
            log.info("回收站定时清理：无过期文件");
            return;
        }
        log.info("回收站定时清理：开始清理 {} 个过期文件", expiredFiles.size());
        for (File file : expiredFiles) {
            try {
                // 物理删除数据库记录
                fileMapper.deleteById(file.getId());
                // 从 OSS 中删除实际文件
                ossUtil.deleteFile(file.getStoredFilename());
                log.info("回收站定时清理成功: fileId={}, filename={}", file.getId(), file.getOriginalFilename());
            } catch (Exception e) {
                // 单个文件清理失败不影响后续文件的清理
                log.error("回收站定时清理失败: fileId={}, 原因={}", file.getId(), e.getMessage());
            }
        }
        log.info("回收站定时清理：完成，成功清理 {} 个文件", expiredFiles.size());
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
        vo.setDeleted(file.getDeleted());
        vo.setDeleteTime(file.getDeleteTime());
        return vo;
    }
}