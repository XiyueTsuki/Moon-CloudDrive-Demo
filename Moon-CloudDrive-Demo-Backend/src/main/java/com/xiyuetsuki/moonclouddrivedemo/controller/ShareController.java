package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.VerifyCodeRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分享管理", description = "文件分享创建、查询、取消、提取码验证、下载等接口")
@RestController
@Slf4j
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @Operation(summary = "创建分享链接", description = "为指定文件创建一个分享链接，可设置有效期与提取码")
    @PostMapping("/api/share/create")
    public Response<Share> createShare(@Valid @RequestBody CreateShareRequest request) {
        Share share = shareService.createShare(request);
        return Response.ok(share, "分享链接创建成功");
    }

    @Operation(summary = "查询我的分享", description = "查询当前用户创建的所有分享链接")
    @GetMapping("/api/share/my")
    public Response<List<Share>> getMyShares() {
        List<Share> shares = shareService.getMyShares();
        return Response.ok(shares, "查询成功");
    }

    @Operation(summary = "取消分享", description = "取消指定分享链接，分享将立即失效")
    @DeleteMapping("/api/share/{shareCode}")
    public Response<Void> cancelShare(
            @Parameter(description = "分享码") @PathVariable String shareCode) {
        shareService.cancelShare(shareCode);
        return Response.ok("分享已取消");
    }

    @Operation(summary = "获取分享信息", description = "根据分享码获取分享文件的详细信息（无需登录）")
    @GetMapping("/share/{shareCode}")
    public Response<ShareInfoResponse> getShareInfo(
            @Parameter(description = "分享码") @PathVariable String shareCode) {
        ShareInfoResponse info = shareService.getShareInfo(shareCode);
        return Response.ok(info, "查询成功");
    }

    @Operation(summary = "验证分享提取码", description = "验证分享链接的提取码，验证通过后才能下载")
    @RateLimit(dimension = RateLimitDimension.IP, maxRequests = 5, windowSeconds = 60, message = "提取码验证过于频繁，请稍后再试")
    @PostMapping("/share/{shareCode}/verify")
    public Response<Void> verifyPassword(
            @Parameter(description = "分享码") @PathVariable String shareCode,
            @Valid @RequestBody VerifyCodeRequest request) {
        shareService.verifyPassword(shareCode, request.getPassword());
        return Response.ok("验证成功");
    }

    @Operation(summary = "获取分享文件下载链接", description = "获取分享文件的OSS预签名下载URL，有提取码时需传入已验证的密码")
    @GetMapping("/share/{shareCode}/download")
    public Response<String> getDownloadUrl(
            @Parameter(description = "分享码") @PathVariable String shareCode,
            @Parameter(description = "提取码（若分享设置了提取码则必传）") @RequestParam(required = false) String password) {
        String downloadUrl = shareService.getDownloadUrl(shareCode, password);
        return Response.ok(downloadUrl, "获取下载链接成功");
    }

    // ==================== 分享文件夹打包下载 ====================

    /**
     * 提交分享文件夹的打包下载任务
     * 校验分享后递归收集文件夹下所有文件，提交异步打包任务，返回 taskId 供前端轮询
     *
     * @param shareCode 分享码
     * @param password  提取码（可选）
     * @return 包含 taskId 的响应
     */
    @Operation(summary = "提交分享文件夹打包下载", description = "提交分享文件夹的打包下载任务，返回taskId供前端轮询进度")
    @RateLimit(dimension = RateLimitDimension.IP, maxRequests = 3, windowSeconds = 60,
            message = "打包下载过于频繁，请1分钟后再试")
    @PostMapping("/share/{shareCode}/prepare-pack")
    public Response<String> prepareSharePackDownload(
            @Parameter(description = "分享码") @PathVariable String shareCode,
            @Parameter(description = "提取码") @RequestParam(required = false) String password) {
        String taskId = shareService.prepareSharePackDownload(shareCode, password);
        return Response.ok(taskId, "打包任务已提交");
    }

    /**
     * 查询分享文件夹打包进度
     * 前端轮询此接口获取打包的实时进度
     *
     * @param shareCode 分享码
     * @param taskId    打包任务ID
     * @return 包含状态、百分比、消息的进度信息
     */
    @Operation(summary = "查询分享打包进度", description = "前端轮询此接口获取分享文件夹打包的实时进度")
    @GetMapping("/share/{shareCode}/pack-progress")
    public Response<PackProgressResponse> getSharePackProgress(
            @Parameter(description = "分享码") @PathVariable String shareCode,
            @Parameter(description = "打包任务ID") @RequestParam String taskId) {
        PackProgressResponse progress = shareService.getSharePackProgress(shareCode, taskId);
        if (progress == null) {
            return Response.bad(404, "任务不存在或已过期");
        }
        return Response.ok(progress, "查询成功");
    }

    /**
     * 下载分享文件夹打包完成的 ZIP 文件
     * 以流式方式返回 ZIP 文件，浏览器自动触发下载
     *
     * @param shareCode 分享码
     * @param taskId    打包任务ID
     */
    @Operation(summary = "下载分享打包ZIP", description = "下载分享文件夹打包完成的ZIP文件")
    @GetMapping("/share/{shareCode}/pack-download")
    public void downloadSharePackZip(
            @Parameter(description = "分享码") @PathVariable String shareCode,
            @Parameter(description = "打包任务ID") @RequestParam String taskId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        PackProgressResponse progress = shareService.getSharePackProgress(shareCode, taskId);
        if (progress == null) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":404,\"msg\":\"任务不存在或已过期\"}");
            return;
        }
        if (!"ready".equals(progress.getStatus())) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"msg\":\"打包尚未完成，当前状态: "
                    + progress.getStatus() + "\"}");
            return;
        }

        String zipPath = shareService.getSharePackFilePath(shareCode, taskId);
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

        String filename = progress.getZipFilename() != null
                ? progress.getZipFilename() : "pack_download.zip";
        String encodedFilename = java.net.URLEncoder.encode(filename,
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

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

        log.info("分享文件夹ZIP下载完成: shareCode={}, taskId={}, size={}",
                shareCode, taskId, zipFile.length());
    }
}