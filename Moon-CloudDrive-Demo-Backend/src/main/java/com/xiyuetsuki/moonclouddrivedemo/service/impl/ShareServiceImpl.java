package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.mapper.ShareMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareExpireManager;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareMapper shareMapper;
    private final FileMapper fileMapper;
    private final OssUtil ossUtil;
    private final PasswordEncoder passwordEncoder;
    private final ShareExpireManager shareExpireManager;

    @Override
    public Share createShare(CreateShareRequest request) {
        long userId = StpUtil.getLoginIdAsLong();

        File file = fileMapper.selectById(request.getFileId());
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权分享此文件");
        }

        Share share = new Share();
        share.setShareCode(generateShareCode());
        share.setFileId(request.getFileId());
        share.setUserId(userId);

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            share.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        int expireHours = request.getExpireHours() != null ? request.getExpireHours() : 168;
        share.setExpireTime(LocalDateTime.now().plusHours(expireHours));

        share.setMaxDownloads(request.getMaxDownloads() != null ? request.getMaxDownloads() : -1);
        share.setDownloadCount(0);
        share.setStatus(1);

        shareMapper.insert(share);

        shareExpireManager.schedule(share.getShareCode(), share.getExpireTime());

        log.info("分享链接已创建: code={}, fileId={}, expireTime={}", share.getShareCode(), request.getFileId(), share.getExpireTime());
        return share;
    }

    @Override
    public ShareInfoResponse getShareInfo(String shareCode) {
        Share share = validateShare(shareCode);
        File file = fileMapper.selectById(share.getFileId());

        if (share.getPassword() != null) {
            return new ShareInfoResponse(shareCode, file.getOriginalFilename(), file.getFileSize(), true, null);
        }

        String downloadUrl = ossUtil.generatePresignedUrl(file.getStoredFilename(), file.getOriginalFilename());
        share.setDownloadCount(share.getDownloadCount() + 1);
        shareMapper.updateById(share);

        return new ShareInfoResponse(shareCode, file.getOriginalFilename(), file.getFileSize(), false, downloadUrl);
    }

    @Override
    public String verifyPassword(String shareCode, String password) {
        Share share = validateShare(shareCode);

        if (share.getPassword() == null) {
            throw new RuntimeException("此链接无需提取码");
        }

        if (!passwordEncoder.matches(password, share.getPassword())) {
            throw new RuntimeException("提取码错误");
        }

        File file = fileMapper.selectById(share.getFileId());
        String downloadUrl = ossUtil.generatePresignedUrl(file.getStoredFilename(), file.getOriginalFilename());

        share.setDownloadCount(share.getDownloadCount() + 1);
        shareMapper.updateById(share);

        return downloadUrl;
    }

    @Override
    public List<Share> getMyShares() {
        long userId = StpUtil.getLoginIdAsLong();
        return shareMapper.selectByUserId(userId);
    }

    @Override
    public void cancelShare(String shareCode) {
        long userId = StpUtil.getLoginIdAsLong();
        Share share = shareMapper.selectByShareCode(shareCode);
        if (share == null) {
            throw new RuntimeException("分享链接不存在");
        }
        if (!share.getUserId().equals(userId)) {
            throw new RuntimeException("无权取消此分享");
        }
        share.setStatus(0);
        shareMapper.updateById(share);
        log.info("分享链接已取消: code={}", shareCode);
    }

    private Share validateShare(String shareCode) {
        Share share = shareMapper.selectByShareCode(shareCode);
        if (share == null) {
            throw new RuntimeException("分享链接不存在");
        }
        if (share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("分享链接已过期");
        }
        if (share.getStatus() == 0) {
            throw new RuntimeException("分享链接已失效");
        }
        if (share.getMaxDownloads() > 0 && share.getDownloadCount() >= share.getMaxDownloads()) {
            throw new RuntimeException("下载次数已用完");
        }
        return share;
    }

    private String generateShareCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        byte[] bytes = new byte[CODE_LENGTH];
        RANDOM.nextBytes(bytes);
        for (byte b : bytes) {
            sb.append(BASE62.charAt(Math.abs(b) % BASE62.length()));
        }
        return sb.toString();
    }
}