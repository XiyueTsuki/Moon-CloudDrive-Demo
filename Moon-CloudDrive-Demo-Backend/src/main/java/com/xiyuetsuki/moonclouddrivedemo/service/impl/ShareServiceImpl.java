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

        // 仅返回文件元数据，不返回下载链接，不递增下载次数
        // 下载链接需通过 getDownloadUrl 接口单独获取
        return new ShareInfoResponse(shareCode, file.getOriginalFilename(), file.getFileSize(),
                share.getPassword() != null, null);
    }

    @Override
    public void verifyPassword(String shareCode, String password) {
        Share share = validateShare(shareCode);

        if (share.getPassword() == null) {
            throw new RuntimeException("此链接无需提取码");
        }

        if (!passwordEncoder.matches(password, share.getPassword())) {
            throw new RuntimeException("提取码错误");
        }

        // 仅校验提取码，不返回下载链接，不递增下载次数
        // 下载次数在实际下载时由 getDownloadUrl 递增
    }

    @Override
    public String getDownloadUrl(String shareCode, String password) {
        // 重新校验分享链接有效性
        Share share = validateShare(shareCode);

        // 如果分享设置了提取码，则校验密码
        if (share.getPassword() != null) {
            if (password == null || password.isEmpty()) {
                throw new RuntimeException("此链接需要提取码");
            }
            if (!passwordEncoder.matches(password, share.getPassword())) {
                throw new RuntimeException("提取码错误");
            }
        }

        File file = fileMapper.selectById(share.getFileId());
        String downloadUrl = ossUtil.generatePresignedUrl(file.getStoredFilename(), file.getOriginalFilename());

        // 递增下载次数
        int newCount = share.getDownloadCount() + 1;
        share.setDownloadCount(newCount);

        // 达到最大下载次数时，立即将链接状态置为失效
        if (share.getMaxDownloads() > 0 && newCount >= share.getMaxDownloads()) {
            share.setStatus(0);
            log.info("分享链接已达最大下载次数，自动失效: code={}, downloadCount={}/{}",
                    shareCode, newCount, share.getMaxDownloads());
        }

        shareMapper.updateById(share);

        log.info("分享文件下载: code={}, downloadCount={}/{}", shareCode, newCount, share.getMaxDownloads());
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

    /**
     * 校验分享链接有效性
     * 若检测到已过期或下载次数已用完，自动将数据库状态置为失效
     * @param shareCode 分享码
     * @return 有效的分享实体
     */
    private Share validateShare(String shareCode) {
        Share share = shareMapper.selectByShareCode(shareCode);
        if (share == null) {
            throw new RuntimeException("分享链接不存在");
        }

        // 已手动取消的链接直接拒绝
        if (share.getStatus() == 0) {
            throw new RuntimeException("分享链接已失效");
        }

        boolean shouldExpire = false;

        // 检查是否超过有效期
        if (share.getExpireTime().isBefore(LocalDateTime.now())) {
            shouldExpire = true;
        }

        // 检查是否达到最大下载次数
        if (share.getMaxDownloads() > 0 && share.getDownloadCount() >= share.getMaxDownloads()) {
            shouldExpire = true;
        }

        // 检测到失效条件时，更新数据库状态并抛出异常
        if (shouldExpire) {
            share.setStatus(0);
            shareMapper.updateById(share);
            log.info("分享链接自动失效: code={}, reason={}", shareCode,
                    share.getExpireTime().isBefore(LocalDateTime.now()) ? "已过期" : "下载次数已用完");
            throw new RuntimeException("分享链接已失效");
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