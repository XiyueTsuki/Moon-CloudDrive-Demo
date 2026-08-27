package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.VerifyCodeRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/api/share/create")
    public Response<Share> createShare(@RequestBody CreateShareRequest request) {
        if (request.getFileId() == null) {
            return Response.bad(400, "文件ID不能为空");
        }
        Share share = shareService.createShare(request);
        return Response.ok(share, "分享链接创建成功");
    }

    @GetMapping("/api/share/my")
    public Response<List<Share>> getMyShares() {
        List<Share> shares = shareService.getMyShares();
        return Response.ok(shares, "查询成功");
    }

    @DeleteMapping("/api/share/{shareCode}")
    public Response<Void> cancelShare(@PathVariable String shareCode) {
        shareService.cancelShare(shareCode);
        return Response.ok("分享已取消");
    }

    @GetMapping("/s/{shareCode}")
    public Response<ShareInfoResponse> getShareInfo(@PathVariable String shareCode) {
        ShareInfoResponse info = shareService.getShareInfo(shareCode);
        return Response.ok(info, "查询成功");
    }

    @RateLimit(dimension = RateLimitDimension.IP, maxRequests = 5, windowSeconds = 60, message = "提取码验证过于频繁，请稍后再试")
    @PostMapping("/s/{shareCode}/verify")
    public Response<String> verifyPassword(@PathVariable String shareCode, @RequestBody VerifyCodeRequest request) {
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return Response.bad(400, "提取码不能为空");
        }
        String downloadUrl = shareService.verifyPassword(shareCode, request.getPassword());
        return Response.ok(downloadUrl, "验证成功");
    }
}