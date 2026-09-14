package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PageResult;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.UploadProgress;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件控制器，提供文件上传、查询、下载、删除、重命名、回收站等 RESTful API 接口
 */
@RestController
@RequestMapping("/api/file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final ProgressTracker progressTracker;

    /**
     * 文件上传接口
     * 接收文件并提交异步上传任务，返回任务ID供前端轮询进度
     *
     * @param file     上传的文件
     * @param parentId 父文件夹ID，NULL表示上传到根目录
     * @return 包含任务ID的响应
     */
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 5, windowSeconds = 60, message = "上传过于频繁，请1分钟后再试")
    @PostMapping("/upload")
    public Response<String> uploadFile(MultipartFile file, @RequestParam(required = false) Long parentId) {
        if (file.isEmpty()) {
            return Response.bad(400, "文件不能为空");
        }

        try {
            String taskId = fileService.uploadFile(file, parentId);
            return Response.ok(taskId, "上传任务已提交");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 查询上传进度接口
     * 前端轮询此接口获取文件上传的实时进度
     *
     * @param taskId 上传任务ID
     * @return 包含百分比、状态、消息的进度信息
     */
    @GetMapping("/progress")
    public Response<UploadProgress> getProgress(@RequestParam String taskId) {
        UploadProgress progress = progressTracker.get(taskId);
        if (progress == null) {
            return Response.bad(404, "任务不存在或已过期");
        }
        log.debug("查询进度: taskId={}, percent={}, status={}", taskId, progress.getPercent(), progress.getStatus());
        return Response.ok(progress, "查询成功");
    }

    /**
     * 文件列表查询接口（分页 + 排序 + 搜索）
     * 返回当前登录用户指定文件夹下的文件和文件夹列表（不含回收站），
     * 文件夹始终排在前，其余按指定字段排序
     *
     * @param parentId  父文件夹ID，不传则查询根目录
     * @param page      页码（从1开始，默认1）
     * @param size      每页条数（默认20）
     * @param sortBy    排序字段：name / size / uploadTime（默认 uploadTime）
     * @param sortOrder 排序方向：asc / desc（默认 desc）
     * @param keyword   按文件名搜索（模糊匹配），不传则不搜索
     * @return 分页结果
     */
    @GetMapping("/list")
    public Response<PageResult<FileVO>> listFiles(@RequestParam(required = false) Long parentId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(defaultValue = "uploadTime") String sortBy,
                                                   @RequestParam(defaultValue = "desc") String sortOrder,
                                                   @RequestParam(required = false) String keyword) {
        PageResult<FileVO> result = fileService.listFiles(parentId, page, size, sortBy, sortOrder, keyword);
        return Response.ok(result, "查询成功");
    }

    /**
     * 文件/文件夹删除接口（软删除）
     * 将文件或文件夹移入回收站，30天后自动清理。
     * 文件夹删除会递归删除目录下所有子孙文件/文件夹
     *
     * @param fileId 文件/文件夹ID
     * @return 操作结果
     */
    @DeleteMapping("/delete")
    public Response<Void> deleteFile(@RequestParam Long fileId) {
        try {
            fileService.deleteFile(fileId);
            return Response.ok("文件已移入回收站");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 文件/文件夹重命名接口
     * 仅允许重命名自己的文件或文件夹
     *
     * @param fileId  文件/文件夹ID
     * @param newName 新名称
     * @return 操作结果
     */
    @PutMapping("/rename")
    public Response<Void> renameFile(@RequestParam Long fileId, @RequestParam String newName) {
        try {
            fileService.renameFile(fileId, newName);
            return Response.ok("重命名成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 获取文件下载链接接口
     * 返回OSS预签名URL，前端可直接使用该URL下载文件
     *
     * @param fileId 文件ID
     * @return 包含预签名下载URL的响应
     */
    @GetMapping("/download")
    public Response<String> getDownloadUrl(@RequestParam Long fileId) {
        try {
            String downloadUrl = fileService.getDownloadUrl(fileId);
            return Response.ok(downloadUrl, "获取下载链接成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    // ==================== 回收站相关接口 ====================

    /**
     * 回收站文件列表查询接口
     * 返回当前登录用户回收站中的所有文件
     *
     * @return 回收站文件列表
     */
    @GetMapping("/recycle-bin/list")
    public Response<List<FileVO>> listRecycleBin() {
        List<FileVO> files = fileService.listRecycleBin();
        return Response.ok(files, "查询成功");
    }

    /**
     * 回收站文件恢复接口
     * 将回收站中的文件恢复为正常状态
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @PutMapping("/recycle-bin/restore")
    public Response<Void> restoreFile(@RequestParam Long fileId) {
        try {
            fileService.restoreFile(fileId);
            return Response.ok("文件恢复成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 回收站文件彻底删除接口
     * 物理删除文件/文件夹记录并从OSS中删除实际文件，不可恢复。
     * 文件夹会递归删除所有子孙节点
     *
     * @param fileId 文件/文件夹ID
     * @return 操作结果
     */
    @DeleteMapping("/recycle-bin/permanent-delete")
    public Response<Void> permanentDeleteFile(@RequestParam Long fileId) {
        try {
            fileService.permanentDeleteFile(fileId);
            return Response.ok("已彻底删除");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    // ==================== 文件夹相关接口 ====================

    /**
     * 创建文件夹接口
     *
     * @param folderName 文件夹名称
     * @param parentId   父文件夹ID，不传则创建在根目录
     * @return 创建的文件夹信息
     */
    @PostMapping("/folder/create")
    public Response<FileVO> createFolder(@RequestParam String folderName,
                                         @RequestParam(required = false) Long parentId) {
        try {
            FileVO folder = fileService.createFolder(folderName, parentId);
            return Response.ok(folder, "文件夹创建成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 移动文件/文件夹接口
     * 支持移动文件或整个文件夹到目标目录，后端会校验循环引用
     *
     * @param fileId         要移动的文件/文件夹ID
     * @param targetParentId 目标父文件夹ID，不传则移动到根目录
     * @return 操作结果
     */
    @PutMapping("/folder/move")
    public Response<Void> moveFile(@RequestParam Long fileId,
                                   @RequestParam(required = false) Long targetParentId) {
        try {
            fileService.moveFile(fileId, targetParentId);
            return Response.ok("移动成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 获取文件夹路径（面包屑导航）接口
     * 返回从根目录到指定文件夹的完整路径链
     *
     * @param folderId 文件夹ID，不传返回空列表
     * @return 文件夹路径链，从根到该文件夹
     */
    @GetMapping("/folder/path")
    public Response<List<FileVO>> getFolderPath(@RequestParam(required = false) Long folderId) {
        List<FileVO> path = fileService.getFolderPath(folderId);
        return Response.ok(path, "查询成功");
    }
}