package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
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
}