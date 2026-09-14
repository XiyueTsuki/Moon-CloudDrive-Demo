package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PageResult;
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
    public String uploadFile(MultipartFile file, Long parentId) {
        /*
        生成上传任务ID(UUID) -> 校验目标父文件夹是否合法 ->
        Redis初始化上传进度 -> 文件内容转为字节数组 ->
        提交给异步上传 -> 返回上传任务ID
         */

        // 生成唯一任务ID，用于追踪上传进度
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long userId = StpUtil.getLoginIdAsLong();
        String originalFilename = file.getOriginalFilename();

        // 校验目标文件夹是否存在且合法
        if (parentId != null) {
            File parentFolder = fileMapper.selectByUserIdAndId(userId, parentId);
            if (parentFolder == null || parentFolder.getIsFolder() == null || parentFolder.getIsFolder() != 1) {
                throw new RuntimeException("目标文件夹不存在或不是有效文件夹");
            }
        }

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

        // 提交异步上传任务，传递 parentId 参数
        asyncUploadService.execute(taskId, userId, originalFilename,
                fileBytes, file.getSize(), file.getContentType(), parentId);

        return taskId;
    }

    @Override
    public PageResult<FileVO> listFiles(Long parentId, int page, int size,
                                        String sortBy, String sortOrder, String keyword) {
        long userId = StpUtil.getLoginIdAsLong();

        // 白名单校验：排序字段映射为数据库列名，防止 SQL 注入
        String sortColumn;
        switch (sortBy) {
            case "name":       sortColumn = "original_filename"; break;
            case "size":       sortColumn = "file_size";         break;
            case "uploadTime": sortColumn = "upload_time";       break;
            default:           sortColumn = "upload_time";       break;
        }

        // 白名单校验：排序方向
        String order = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";

        // 搜索关键词去空白
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        long total = fileMapper.countFiles(userId, parentId, trimmedKeyword);
        int offset = (page - 1) * size;
        List<File> files = fileMapper.selectPage(userId, parentId, trimmedKeyword, sortColumn, order, offset, size);
        List<FileVO> records = files.stream().map(this::toFileVO).collect(Collectors.toList());

        return new PageResult<>(records, total, page, size);
    }

    @Override
    public void deleteFile(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件或文件夹不存在或无权操作");
        }

        // 如果是文件夹，递归获取所有子孙节点一并软删除
        if (file.getIsFolder() != null && file.getIsFolder() == 1) {
            List<File> descendants = fileMapper.selectAllDescendants(fileId);
            LocalDateTime now = LocalDateTime.now();
            int count = 0;
            for (File f : descendants) {
                f.setDeleted(1);
                f.setDeleteTime(now);
                fileMapper.updateById(f);
                count++;
            }
            log.info("文件夹已移入回收站（含 {} 个条目）: userId={}, folderId={}, folderName={}",
                    count, userId, fileId, file.getOriginalFilename());
        } else {
            // 普通文件：软删除，设置删除标记和删除时间，文件进入回收站
            file.setDeleted(1);
            file.setDeleteTime(LocalDateTime.now());
            fileMapper.updateById(file);
            log.info("文件已移入回收站: userId={}, fileId={}, filename={}",
                    userId, fileId, file.getOriginalFilename());
        }
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

        // 如果是文件夹，递归恢复所有子孙节点
        if (file.getIsFolder() != null && file.getIsFolder() == 1) {
            List<File> descendants = fileMapper.selectAllDescendants(fileId);
            int count = 0;
            for (File f : descendants) {
                f.setDeleted(0);
                f.setDeleteTime(null);
                fileMapper.updateById(f);
                count++;
            }
            log.info("文件夹已从回收站恢复（含 {} 个条目）: userId={}, folderId={}", count, userId, fileId);
        } else {
            // 普通文件恢复
            file.setDeleted(0);
            file.setDeleteTime(null);
            fileMapper.updateById(file);
            log.info("文件已从回收站恢复: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
        }
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

        // 如果是文件夹，递归彻底删除所有子孙节点
        if (file.getIsFolder() != null && file.getIsFolder() == 1) {
            List<File> descendants = fileMapper.selectAllDescendants(fileId);
            int count = 0;
            for (File f : descendants) {
                // 只有普通文件才需要从 OSS 中删除实际文件
                if (f.getIsFolder() == null || f.getIsFolder() != 1) {
                    ossUtil.deleteFile(f.getStoredFilename());
                }
                fileMapper.deleteById(f.getId());
                count++;
            }
            log.info("文件夹已彻底删除（含 {} 个条目）: userId={}, folderId={}", count, userId, fileId);
        } else {
            // 普通文件：物理删除数据库记录 + OSS 删除
            fileMapper.deleteById(fileId);
            ossUtil.deleteFile(file.getStoredFilename());
            log.info("文件已彻底删除: userId={}, fileId={}, filename={}", userId, fileId, file.getOriginalFilename());
        }
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
                // 文件夹只删记录，普通文件还需从 OSS 删除实际文件
                if (file.getIsFolder() == null || file.getIsFolder() != 1) {
                    ossUtil.deleteFile(file.getStoredFilename());
                }
                fileMapper.deleteById(file.getId());
                log.info("回收站定时清理成功: fileId={}, filename={}", file.getId(), file.getOriginalFilename());
            } catch (Exception e) {
                // 单个文件清理失败不影响后续文件的清理
                log.error("回收站定时清理失败: fileId={}, 原因={}", file.getId(), e.getMessage());
            }
        }
        log.info("回收站定时清理：完成，成功清理 {} 个文件", expiredFiles.size());
    }

    // ==================== 文件夹功能 ====================

    @Override
    public FileVO createFolder(String folderName, Long parentId) {
        long userId = StpUtil.getLoginIdAsLong();

        // 校验文件夹名称
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new RuntimeException("文件夹名称不能为空");
        }
        folderName = folderName.trim();

        // 校验同一父目录下不能有同名文件夹
        int count = fileMapper.countByNameAndParent(userId, parentId, folderName);
        if (count > 0) {
            throw new RuntimeException("该目录下已存在同名文件或文件夹");
        }

        // 如果指定了父文件夹，校验父文件夹存在且合法
        if (parentId != null) {
            File parentFolder = fileMapper.selectByUserIdAndId(userId, parentId);
            if (parentFolder == null || parentFolder.getIsFolder() == null || parentFolder.getIsFolder() != 1) {
                throw new RuntimeException("父文件夹不存在或不是有效文件夹");
            }
        }

        // 构建文件夹实体
        File folder = new File();
        folder.setOriginalFilename(folderName);
        folder.setParentId(parentId);
        folder.setUserId(userId);
        folder.setIsFolder(1);
        folder.setFileSize(0L);
        folder.setFileHash("");
        folder.setStoredFilename("");
        folder.setOssUrl("");
        folder.setUploadTime(LocalDateTime.now());
        folder.setDeleted(0);
        fileMapper.insert(folder);

        log.info("文件夹创建成功: userId={}, folderName={}, parentId={}", userId, folderName, parentId);
        return toFileVO(folder);
    }

    @Override
    public void moveFile(Long fileId, Long targetParentId) {
        long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new RuntimeException("文件或文件夹不存在或无权操作");
        }

        // 不能移动到自身
        if (targetParentId != null && targetParentId.equals(fileId)) {
            throw new RuntimeException("不能将文件夹移动到自身");
        }

        // 如果文件是文件夹，不能移动到其子孙文件夹中（防止循环引用）
        if (file.getIsFolder() != null && file.getIsFolder() == 1 && targetParentId != null) {
            List<File> descendants = fileMapper.selectAllDescendants(fileId);
            for (File descendant : descendants) {
                if (descendant.getId().equals(targetParentId)) {
                    throw new RuntimeException("不能将文件夹移动到其子文件夹中");
                }
            }
        }

        // 校验目标文件夹存在且合法
        if (targetParentId != null) {
            File targetFolder = fileMapper.selectByUserIdAndId(userId, targetParentId);
            if (targetFolder == null || targetFolder.getIsFolder() == null || targetFolder.getIsFolder() != 1) {
                throw new RuntimeException("目标文件夹不存在或不是有效文件夹");
            }
        }

        // 校验目标目录下没有同名文件/文件夹
        int count = fileMapper.countByNameAndParent(userId, targetParentId, file.getOriginalFilename());
        if (count > 0) {
            throw new RuntimeException("目标目录下已存在同名文件或文件夹");
        }

        fileMapper.updateParentId(fileId, targetParentId);
        log.info("移动成功: userId={}, fileId={}, targetParentId={}", userId, fileId, targetParentId);
    }

    @Override
    public List<FileVO> getFolderPath(Long folderId) {
        if (folderId == null) {
            return List.of();
        }
        List<File> path = fileMapper.selectFolderPath(folderId);
        // 使用递归 CTE 查询结果（depth 字段不在 File 实体中），直接手动构建 FileVO 列表
        return path.stream()
                .map(f -> {
                    FileVO vo = new FileVO();
                    vo.setId(f.getId());
                    vo.setOriginalFilename(f.getOriginalFilename());
                    vo.setIsFolder(1);
                    return vo;
                })
                .collect(Collectors.toList());
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
        vo.setParentId(file.getParentId());
        vo.setIsFolder(file.getIsFolder());
        return vo;
    }
}