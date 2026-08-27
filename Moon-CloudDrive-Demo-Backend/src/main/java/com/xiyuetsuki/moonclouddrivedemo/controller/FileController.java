package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
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
 * 文件控制器，提供文件上传、查询、下载、删除、重命名等 RESTful API 接口
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
     * @param file 上传的文件
     * @return 包含任务ID的响应
     */
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 5, windowSeconds = 60, message = "上传过于频繁，请1分钟后再试")
    @PostMapping("/upload")
    public Response<String> uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            return Response.bad(400, "文件不能为空");
        }

        String taskId = fileService.uploadFile(file);

        return Response.ok(taskId, "上传任务已提交");
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
     * 文件列表查询接口
     * 返回当前登录用户的所有文件，按上传时间倒序排列
     *
     * @return 文件信息列表
     */
    @GetMapping("/list")
    public Response<List<FileVO>> listFiles() {
        List<FileVO> files = fileService.listFiles();
        return Response.ok(files, "查询成功");
    }

    /**
     * 文件删除接口
     * 仅允许删除自己上传的文件
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @DeleteMapping("/delete")
    public Response<Void> deleteFile(@RequestParam Long fileId) {
        try {
            fileService.deleteFile(fileId);
            return Response.ok("删除成功");
        } catch (RuntimeException e) {
            return Response.bad(400, e.getMessage());
        }
    }

    /**
     * 文件重命名接口
     * 仅允许重命名自己上传的文件
     *
     * @param fileId  文件ID
     * @param newName 新文件名
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
}