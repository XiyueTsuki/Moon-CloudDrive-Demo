package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.BatchOperationRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.BatchOperationResult;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkCompleteRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkInitRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkInitResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackPrepareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PageResult;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PdfPreviewResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PreviewInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.TextPreviewResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.UploadProgress;
import com.xiyuetsuki.moonclouddrivedemo.service.ChunkUploadService;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.service.PackDownloadService;
import com.xiyuetsuki.moonclouddrivedemo.service.PdfConvertService;
import com.xiyuetsuki.moonclouddrivedemo.service.PreviewService;
import com.xiyuetsuki.moonclouddrivedemo.config.PdfPreviewConfig;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件控制器，提供文件上传、查询、下载、删除、重命名、回收站等 RESTful API 接口
 */
@Tag(name = "文件管理", description = "文件上传、下载、删除、回收站、文件夹管理等接口")
@RestController
@RequestMapping("/api/file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final ProgressTracker progressTracker;
    private final ChunkUploadService chunkUploadService;
    private final PackDownloadService packDownloadService;
    private final PreviewService previewService;
    private final PdfConvertService pdfConvertService;
    private final PdfPreviewConfig pdfPreviewConfig;

    /**
     * 文件上传接口
     * 接收文件并提交异步上传任务，返回任务ID供前端轮询进度
     *
     * @param file     上传的文件
     * @param parentId 父文件夹ID，NULL表示上传到根目录
     * @return 包含任务ID的响应
     */
    @Operation(summary = "文件上传", description = "接收文件并提交异步上传任务，返回任务ID供前端轮询进度")
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 5, windowSeconds = 60, message = "上传过于频繁，请1分钟后再试")
    @PostMapping("/upload")
    public Response<String> uploadFile(
            @Parameter(description = "上传的文件") MultipartFile file,
            @Parameter(description = "父文件夹ID，不传则上传到根目录") @RequestParam(required = false) Long parentId) {
        if (file.isEmpty()) {
            return Response.bad(400, "文件不能为空");
        }

        String taskId = fileService.uploadFile(file, parentId);
        return Response.ok(taskId, "上传任务已提交");
    }

    // ==================== 分片上传（大文件）接口 ====================

    @Operation(summary = "初始化分片上传", description = "开始大文件分片上传，返回uploadId和分片信息，若文件哈希已存在则秒传")
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 10, windowSeconds = 60, message = "操作过于频繁，请稍后再试")
    @PostMapping("/chunk/init")
    public Response<ChunkInitResponse> initChunkUpload(@RequestBody ChunkInitRequest request) {
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            return Response.bad(400, "文件名不能为空");
        }
        if (request.getFileSize() == null || request.getFileSize() <= 0) {
            return Response.bad(400, "文件大小无效");
        }
        if (request.getFileHash() == null || request.getFileHash().isBlank()) {
            return Response.bad(400, "文件哈希不能为空");
        }
        ChunkInitResponse resp = chunkUploadService.initChunkUpload(
                request.getFileName(), request.getFileSize(),
                request.getFileHash(), request.getParentId(), request.getContentType());
        String msg = resp.isInstantComplete() ? "秒传成功" : "分片上传已初始化";
        return Response.ok(resp, msg);
    }

    @Operation(summary = "上传分片", description = "上传单个分片，chunkIndex从0开始")
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 60, windowSeconds = 60, message = "上传过于频繁，请稍后再试")
    @PostMapping("/chunk/upload")
    public Response<Void> uploadChunk(
            @Parameter(description = "分片数据") MultipartFile chunk,
            @Parameter(description = "上传任务ID") @RequestParam String uploadId,
            @Parameter(description = "分片序号，从0开始") @RequestParam int chunkIndex) {
        if (chunk.isEmpty()) {
            return Response.bad(400, "分片不能为空");
        }
        chunkUploadService.uploadChunk(uploadId, chunkIndex, chunk);
        return Response.ok("分片上传成功");
    }

    @Operation(summary = "完成分片上传", description = "所有分片上传完毕后调用此接口合并文件")
    @PostMapping("/chunk/complete")
    public Response<FileVO> completeChunkUpload(@RequestBody ChunkCompleteRequest request) {
        if (request.getUploadId() == null || request.getUploadId().isBlank()) {
            return Response.bad(400, "uploadId不能为空");
        }
        FileVO fileVO = chunkUploadService.completeChunkUpload(
                request.getUploadId(), request.getContentType());
        return Response.ok(fileVO, "文件上传完成");
    }

    @Operation(summary = "查询分片上传进度", description = "查询分片上传进度，用于断点续传时判断哪些分片已上传")
    @GetMapping("/chunk/progress")
    public Response<ChunkProgressResponse> getChunkProgress(
            @Parameter(description = "上传任务ID") @RequestParam String uploadId) {
        ChunkProgressResponse progress = chunkUploadService.getChunkProgress(uploadId);
        return Response.ok(progress, "查询成功");
    }

    @Operation(summary = "取消分片上传", description = "取消分片上传并清理OSS中的碎片")
    @DeleteMapping("/chunk/abort")
    public Response<Void> abortChunkUpload(
            @Parameter(description = "上传任务ID") @RequestParam String uploadId) {
        chunkUploadService.abortChunkUpload(uploadId);
        return Response.ok("分片上传已取消");
    }

    // ==================== 普通上传进度查询 ====================

    /**
     * 查询上传进度接口（小文件上传）
     * 前端轮询此接口获取文件上传的实时进度
     *
     * @param taskId 上传任务ID
     * @return 包含百分比、状态、消息的进度信息
     */
    @Operation(summary = "查询上传进度", description = "前端轮询此接口获取文件上传的实时进度")
    @GetMapping("/progress")
    public Response<UploadProgress> getProgress(
            @Parameter(description = "上传任务ID") @RequestParam String taskId) {
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
    @Operation(summary = "文件列表查询", description = "分页查询当前用户的文件列表，支持排序和关键词搜索，文件夹始终排在前面")
    @GetMapping("/list")
    public Response<PageResult<FileVO>> listFiles(
            @Parameter(description = "父文件夹ID，不传则查询根目录") @RequestParam(required = false) Long parentId,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序字段：name / size / uploadTime") @RequestParam(defaultValue = "uploadTime") String sortBy,
            @Parameter(description = "排序方向：asc / desc") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "按文件名模糊搜索") @RequestParam(required = false) String keyword) {
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
    @Operation(summary = "删除文件/文件夹", description = "软删除，将文件或文件夹移入回收站，30天后自动清理")
    @DeleteMapping("/delete")
    public Response<Void> deleteFile(
            @Parameter(description = "文件/文件夹ID") @RequestParam Long fileId) {
        fileService.deleteFile(fileId);
        return Response.ok("文件已移入回收站");
    }

    /**
     * 文件/文件夹重命名接口
     * 仅允许重命名自己的文件或文件夹
     *
     * @param fileId  文件/文件夹ID
     * @param newName 新名称
     * @return 操作结果
     */
    @Operation(summary = "重命名文件/文件夹", description = "修改文件或文件夹的名称")
    @PutMapping("/rename")
    public Response<Void> renameFile(
            @Parameter(description = "文件/文件夹ID") @RequestParam Long fileId,
            @Parameter(description = "新名称") @RequestParam String newName) {
        fileService.renameFile(fileId, newName);
        return Response.ok("重命名成功");
    }

    /**
     * 获取文件下载链接接口
     * 返回OSS预签名URL，前端可直接使用该URL下载文件
     *
     * @param fileId 文件ID
     * @return 包含预签名下载URL的响应
     */
    @Operation(summary = "获取文件下载链接", description = "返回OSS预签名URL，前端可直接使用该URL下载文件")
    @GetMapping("/download")
    public Response<String> getDownloadUrl(
            @Parameter(description = "文件ID") @RequestParam Long fileId) {
        String downloadUrl = fileService.getDownloadUrl(fileId);
        return Response.ok(downloadUrl, "获取下载链接成功");
    }

    // ==================== 回收站相关接口 ====================

    /**
     * 回收站文件列表查询接口
     * 返回当前登录用户回收站中的所有文件
     *
     * @return 回收站文件列表
     */
    @Operation(summary = "回收站列表", description = "查询当前用户回收站中的所有文件")
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
    @Operation(summary = "恢复回收站文件", description = "将回收站中的文件恢复为正常状态")
    @PutMapping("/recycle-bin/restore")
    public Response<Void> restoreFile(
            @Parameter(description = "文件ID") @RequestParam Long fileId) {
        fileService.restoreFile(fileId);
        return Response.ok("文件恢复成功");
    }

    /**
     * 回收站文件彻底删除接口
     * 物理删除文件/文件夹记录并从OSS中删除实际文件，不可恢复。
     * 文件夹会递归删除所有子孙节点
     *
     * @param fileId 文件/文件夹ID
     * @return 操作结果
     */
    @Operation(summary = "彻底删除文件", description = "物理删除文件/文件夹记录并从OSS中删除实际文件，不可恢复")
    @DeleteMapping("/recycle-bin/permanent-delete")
    public Response<Void> permanentDeleteFile(
            @Parameter(description = "文件/文件夹ID") @RequestParam Long fileId) {
        fileService.permanentDeleteFile(fileId);
        return Response.ok("已彻底删除");
    }

    // ==================== 文件夹相关接口 ====================

    /**
     * 创建文件夹接口
     *
     * @param folderName 文件夹名称
     * @param parentId   父文件夹ID，不传则创建在根目录
     * @return 创建的文件夹信息
     */
    @Operation(summary = "创建文件夹", description = "在指定父文件夹下创建新文件夹")
    @PostMapping("/folder/create")
    public Response<FileVO> createFolder(
            @Parameter(description = "文件夹名称") @RequestParam String folderName,
            @Parameter(description = "父文件夹ID，不传则创建在根目录") @RequestParam(required = false) Long parentId) {
        FileVO folder = fileService.createFolder(folderName, parentId);
        return Response.ok(folder, "文件夹创建成功");
    }

    /**
     * 移动文件/文件夹接口
     * 支持移动文件或整个文件夹到目标目录，后端会校验循环引用
     *
     * @param fileId         要移动的文件/文件夹ID
     * @param targetParentId 目标父文件夹ID，不传则移动到根目录
     * @return 操作结果
     */
    @Operation(summary = "移动文件/文件夹", description = "移动文件或整个文件夹到目标目录，后端会校验循环引用")
    @PutMapping("/folder/move")
    public Response<Void> moveFile(
            @Parameter(description = "要移动的文件/文件夹ID") @RequestParam Long fileId,
            @Parameter(description = "目标父文件夹ID，不传则移动到根目录") @RequestParam(required = false) Long targetParentId) {
        fileService.moveFile(fileId, targetParentId);
        return Response.ok("移动成功");
    }

    /**
     * 获取文件夹路径（面包屑导航）接口
     * 返回从根目录到指定文件夹的完整路径链
     *
     * @param folderId 文件夹ID，不传返回空列表
     * @return 文件夹路径链，从根到该文件夹
     */
    @Operation(summary = "获取文件夹路径", description = "返回从根目录到指定文件夹的完整路径链（面包屑导航）")
    @GetMapping("/folder/path")
    public Response<List<FileVO>> getFolderPath(
            @Parameter(description = "文件夹ID，不传返回空列表") @RequestParam(required = false) Long folderId) {
        List<FileVO> path = fileService.getFolderPath(folderId);
        return Response.ok(path, "查询成功");
    }

    // ==================== 多文件打包下载接口 ====================

    /**
     * 提交打包下载任务
     * 接收文件ID列表，发送RocketMQ消息异步处理，返回taskId供前端轮询进度
     *
     * @param request 包含文件ID列表的请求
     * @return 包含taskId的响应
     */
    @Operation(summary = "提交打包下载任务", description = "提交多文件打包下载，返回taskId供前端轮询进度")
    @RateLimit(dimension = RateLimitDimension.USER, maxRequests = 3, windowSeconds = 60, message = "打包下载过于频繁，请1分钟后再试")
    @PostMapping("/pack/prepare")
    public Response<String> preparePackDownload(@RequestBody PackPrepareRequest request) {
        if (request.getFileIds() == null || request.getFileIds().isEmpty()) {
            return Response.bad(400, "请至少选择一个文件");
        }
        String taskId = packDownloadService.preparePack(request.getFileIds());
        return Response.ok(taskId, "打包任务已提交");
    }

    /**
     * 查询打包进度
     * 前端轮询此接口获取打包的实时进度
     *
     * @param taskId 打包任务ID
     * @return 包含状态、百分比、消息的进度信息
     */
    @Operation(summary = "查询打包进度", description = "前端轮询此接口获取打包的实时进度")
    @GetMapping("/pack/progress")
    public Response<PackProgressResponse> getPackProgress(
            @Parameter(description = "打包任务ID") @RequestParam String taskId) {
        PackProgressResponse progress = packDownloadService.getPackProgress(taskId);
        if (progress == null) {
            return Response.bad(404, "任务不存在或已过期");
        }
        return Response.ok(progress, "查询成功");
    }

    /**
     * 下载打包完成的ZIP文件
     * 以流式方式返回ZIP文件，浏览器自动触发下载
     *
     * @param taskId 打包任务ID
     */
    @Operation(summary = "下载打包ZIP文件", description = "下载打包完成的ZIP文件")
    @GetMapping("/pack/download")
    public void downloadPackZip(
            @Parameter(description = "打包任务ID") @RequestParam String taskId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        PackProgressResponse progress = packDownloadService.getPackProgress(taskId);
        if (progress == null) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"任务不存在或已过期\"}");
            return;
        }
        if (!"ready".equals(progress.getStatus())) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"msg\":\"打包尚未完成，当前状态: " + progress.getStatus() + "\"}");
            return;
        }

        String zipPath = packDownloadService.getPackFilePath(taskId);
        if (zipPath == null) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"ZIP文件不存在或已过期\"}");
            return;
        }

        java.io.File zipFile = new java.io.File(zipPath);
        if (!zipFile.exists()) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"ZIP文件不存在或已过期\"}");
            return;
        }

        String filename = progress.getZipFilename() != null ? progress.getZipFilename() : "pack_download.zip";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment;filename=" + encodedFilename);
        response.setContentLengthLong(zipFile.length());

        try (java.io.FileInputStream fis = new java.io.FileInputStream(zipFile);
             java.io.OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        }

        log.info("ZIP下载完成: taskId={}, size={}", taskId, zipFile.length());
    }

    // ==================== 文件预览接口 ====================

    /**
     * 获取文件预览信息
     * 根据文件扩展名返回对应的预览策略：图片 → OSS 处理 URL、视频/音频/PDF → OSS 预签名 URL、文本 → 语言标识、其他 → 不支持
     *
     * @param fileId 文件 ID
     * @return 预览信息，前端根据 previewType 选择对应渲染组件
     */
    @Operation(summary = "获取文件预览信息", description = "根据文件类型返回不同的预览方式：图片/视频/音频/PDF返回URL，文本返回语言标识，其他返回不支持")
    @GetMapping("/preview/info")
    public Response<PreviewInfoResponse> getPreviewInfo(
            @Parameter(description = "文件ID") @RequestParam Long fileId) {
        if (fileId == null) {
            return Response.bad(400, "文件ID不能为空");
        }
        PreviewInfoResponse info = previewService.getPreviewInfo(fileId);
        return Response.ok(info, "查询成功");
    }

    /**
     * 获取文本文件内容（用于代码高亮预览）
     * 仅支持 txt/md/json/xml/yaml/java/py/js/html/css/sql/sh 等文本/代码文件，最大 1MB
     *
     * @param fileId 文件 ID
     * @return 文本内容及语言标识，前端使用 highlight.js / Monaco Editor 渲染
     */
    @Operation(summary = "获取文本文件内容", description = "仅支持文本/代码类文件，最大1MB，返回内容供前端代码高亮渲染")
    @GetMapping("/preview/text")
    public Response<TextPreviewResponse> getTextContent(
            @Parameter(description = "文件ID") @RequestParam Long fileId) {
        if (fileId == null) {
            return Response.bad(400, "文件ID不能为空");
        }
        TextPreviewResponse text = previewService.getTextContent(fileId);
        return Response.ok(text, "查询成功");
    }

    /**
     * 文件预览流式代理（安全模式）
     * 不暴露 OSS URL，由服务端中转文件流到浏览器。适用于需要隐藏 OSS 地址的场景。
     * 浏览器可直接作为 img src / video src / iframe src 使用此端点
     *
     * @param fileId   文件 ID
     * @param response HTTP 响应对象
     */
    @Operation(summary = "文件预览流式代理", description = "不暴露OSS URL，由服务端中转文件流。可作为img/video/iframe的src直接使用")
    @GetMapping("/preview/stream")
    public void previewStream(
            @Parameter(description = "文件ID") @RequestParam Long fileId,
            HttpServletResponse response) {
        previewService.previewStream(fileId, response);
    }

    // ==================== PDF 服务端转图片预览接口 ====================

    /**
     * 获取 PDF 预览信息（服务端转图片模式）
     * 返回总页数和每页图片的 OSS URL 列表。
     * 首次访问触发异步转换，status=converting 时前端应轮询直到 status=ready。
     *
     * @param fileId 文件 ID
     * @return PDF 预览信息，包含总页数、状态、每页图片 URL
     */
    @Operation(summary = "获取PDF预览信息", description = "返回PDF总页数及每页图片URL的OSS预签名地址，首次访问触发异步PDF→图片转换")
    @GetMapping("/preview/pdf")
    public Response<PdfPreviewResponse> getPdfPreview(
            @Parameter(description = "文件ID") @RequestParam Long fileId) {
        if (fileId == null) {
            return Response.bad(400, "文件ID不能为空");
        }
        PdfPreviewResponse preview = pdfConvertService.getPdfPreview(fileId);
        return Response.ok(preview, "查询成功");
    }

    /**
     * 获取 PDF 单页图片流
     * 作为 img src 直接使用，服务端从 OSS 读取已转换的页面图片并代理输出。
     * 设置 Cache-Control 为 24 小时，浏览器侧减少重复请求。
     *
     * @param fileId   文件 ID
     * @param pageNum  页码（从 1 开始）
     * @param response HTTP 响应对象
     */
    @Operation(summary = "获取PDF单页图片", description = "返回指定页的PNG图片流，可直接作为img标签的src属性使用。浏览器缓存24小时")
    @GetMapping("/preview/pdf/page/{pageNum}")
    public void getPdfPageImage(
            @Parameter(description = "文件ID") @RequestParam Long fileId,
            @Parameter(description = "页码，从1开始") @PathVariable int pageNum,
            HttpServletResponse response) throws IOException {

        if (fileId == null) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"msg\":\"文件ID不能为空\"}");
            return;
        }

        byte[] imageBytes = pdfConvertService.getPageImage(fileId, pageNum);
        response.setContentType("image/" + pdfPreviewConfig.getImageFormat());
        response.setContentLength(imageBytes.length);
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.getOutputStream().write(imageBytes);
        response.getOutputStream().flush();
    }

    // ==================== 批量操作接口 ====================

    /**
     * 批量删除文件/文件夹（移入回收站）
     *
     * @param request 包含 fileIds 的批量操作请求
     * @return 批量操作结果
     */
    @Operation(summary = "批量删除", description = "批量将文件/文件夹移入回收站，文件夹会递归删除所有子孙节点")
    @PostMapping("/batch/delete")
    public Response<BatchOperationResult> batchDelete(
            @RequestBody BatchOperationRequest request) {
        BatchOperationResult result = fileService.batchDelete(request.getFileIds());
        if (result.getFailCount() > 0 && result.getSuccessCount() == 0) {
            return Response.bad(400, result, "批量删除失败");
        }
        return Response.ok(result, "成功删除 " + result.getSuccessCount() + " 个文件");
    }

    /**
     * 批量移动文件/文件夹到目标目录
     *
     * @param request 包含 fileIds 和 targetParentId 的批量操作请求
     * @return 批量操作结果
     */
    @Operation(summary = "批量移动", description = "批量移动文件/文件夹到指定目录，后端校验循环引用和同名冲突")
    @PostMapping("/batch/move")
    public Response<BatchOperationResult> batchMove(
            @RequestBody BatchOperationRequest request) {
        BatchOperationResult result = fileService.batchMove(request.getFileIds(), request.getTargetParentId());
        if (result.getFailCount() > 0 && result.getSuccessCount() == 0) {
            return Response.bad(400, result, "批量移动失败");
        }
        return Response.ok(result, "成功移动 " + result.getSuccessCount() + " 个文件");
    }

    /**
     * 批量重命名文件/文件夹
     *
     * @param request 包含 fileIds、mode、value 的批量操作请求
     * @return 批量操作结果
     */
    @Operation(summary = "批量重命名", description = "支持序号模板(sequence)、添加前缀(prefix)、添加后缀(suffix)、替换文本(replace)四种模式")
    @PostMapping("/batch/rename")
    public Response<BatchOperationResult> batchRename(
            @RequestBody BatchOperationRequest request) {
        BatchOperationResult result = fileService.batchRename(
                request.getFileIds(), request.getMode(), request.getValue());
        if (result.getFailCount() > 0 && result.getSuccessCount() == 0) {
            return Response.bad(400, result, "批量重命名失败");
        }
        return Response.ok(result, "成功重命名 " + result.getSuccessCount() + " 个文件");
    }
}