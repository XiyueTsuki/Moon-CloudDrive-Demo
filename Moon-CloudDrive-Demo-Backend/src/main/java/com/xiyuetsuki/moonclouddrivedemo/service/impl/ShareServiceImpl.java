package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.mapper.ShareMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.PackDownloadService;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareExpireManager;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private final PackDownloadService packDownloadService;

    @Override
    public Share createShare(CreateShareRequest request) {
        long userId = StpUtil.getLoginIdAsLong();

        File file = fileMapper.selectById(request.getFileId());
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权分享此文件");
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
        boolean isFolder = file.getIsFolder() != null && file.getIsFolder() == 1;
        return new ShareInfoResponse(shareCode, file.getOriginalFilename(), file.getFileSize(),
                share.getPassword() != null, isFolder, null);
    }

    @Override
    public void verifyPassword(String shareCode, String password) {
        Share share = validateShare(shareCode);

        if (share.getPassword() == null) {
            throw new BusinessException("此链接无需提取码");
        }

        if (!passwordEncoder.matches(password, share.getPassword())) {
            throw new BusinessException("提取码错误");
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
                throw new BusinessException("此链接需要提取码");
            }
            if (!passwordEncoder.matches(password, share.getPassword())) {
                throw new BusinessException("提取码错误");
            }
        }

        File file = fileMapper.selectById(share.getFileId());

        // 文件夹不支持直接下载，应使用打包下载接口
        if (file.getIsFolder() != null && file.getIsFolder() == 1) {
            throw new BusinessException("文件夹不支持直接下载，请使用打包下载");
        }

        String downloadUrl = ossUtil.generatePresignedUrl(file.getStoredFilename(), file.getOriginalFilename());

        // 原子递增下载次数 + 判断是否达到最大下载限制
        // MySQL InnoDB 行级锁保证并发安全，不会漏计或超计
        int rows = shareMapper.incrementDownloadCountAndCheckLimit(share.getId());
        if (rows > 0) {
            log.info("分享文件下载: code={}", shareCode);
        } else {
            // 更新失败说明分享已失效或达到上限，重新查询确认状态用于日志
            Share latest = shareMapper.selectById(share.getId());
            if (latest != null && latest.getMaxDownloads() > 0
                    && latest.getDownloadCount() >= latest.getMaxDownloads()) {
                log.info("分享链接已达最大下载次数，自动失效: code={}, downloadCount={}/{}",
                        shareCode, latest.getDownloadCount(), latest.getMaxDownloads());
            } else {
                log.info("分享链接已失效: code={}", shareCode);
            }
            throw new BusinessException("分享链接已失效");
        }

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
            throw new BusinessException("分享链接不存在");
        }
        if (!share.getUserId().equals(userId)) {
            throw new BusinessException("无权取消此分享");
        }
        share.setStatus(0);
        shareMapper.updateById(share);
        log.info("分享链接已取消: code={}", shareCode);
    }

    @Override
    public String prepareSharePackDownload(String shareCode, String password) {
        // 1. 校验分享链接及提取码
        Share share = validateShare(shareCode);

        if (share.getPassword() != null) {
            if (password == null || password.isEmpty()) {
                throw new BusinessException("此链接需要提取码");
            }
            if (!passwordEncoder.matches(password, share.getPassword())) {
                throw new BusinessException("提取码错误");
            }
        }

        // 2. 确认分享对象是文件夹
        File folder = fileMapper.selectById(share.getFileId());
        if (folder.getIsFolder() == null || folder.getIsFolder() != 1) {
            throw new BusinessException("此分享不是文件夹，请使用下载链接");
        }

        // 3. 递归收集文件夹下所有子孙文件（排除子文件夹，只取实际文件）
        List<File> descendants = fileMapper.selectAllDescendants(folder.getId());
        List<Long> fileIds = new ArrayList<>();
        for (File f : descendants) {
            // 跳过子文件夹本身（文件夹没有 storedFilename，无法从 OSS 下载）
            if (f.getIsFolder() != null && f.getIsFolder() == 1) {
                continue;
            }
            // 跳过存储信息异常的文件
            if (f.getStoredFilename() == null || f.getStoredFilename().isEmpty()) {
                log.warn("分享打包时跳过存储信息异常的文件: fileId={}, name={}", f.getId(), f.getOriginalFilename());
                continue;
            }
            fileIds.add(f.getId());
        }

        if (fileIds.isEmpty()) {
            throw new BusinessException("该文件夹下没有可下载的文件");
        }

        // 4. 提交异步打包任务（使用文件夹拥有者的 userId）
        String taskId = packDownloadService.submitPackTask(folder.getUserId(), fileIds);

        // 5. 原子递增下载次数（整个文件夹打包下载只计一次）
        int rows = shareMapper.incrementDownloadCountAndCheckLimit(share.getId());
        if (rows <= 0) {
            throw new BusinessException("分享链接已失效");
        }

        log.info("分享文件夹打包任务已提交: code={}, taskId={}, fileCount={}",
                shareCode, taskId, fileIds.size());
        return taskId;
    }

    @Override
    public PackProgressResponse getSharePackProgress(String shareCode, String taskId) {
        // 仅校验分享是否存在（不检查过期/下载次数，因为 prepareSharePackDownload 已完成权限校验）
        // 下载次数已达上限时分享会被标记失效，但打包任务仍需继续执行和下载
        if (shareMapper.selectByShareCode(shareCode) == null) {
            throw new BusinessException("分享链接不存在");
        }
        return packDownloadService.getPackProgress(taskId);
    }

    @Override
    public String getSharePackFilePath(String shareCode, String taskId) {
        // 同上，仅校验分享是否存在，不检查下载次数和过期
        if (shareMapper.selectByShareCode(shareCode) == null) {
            throw new BusinessException("分享链接不存在");
        }
        return packDownloadService.getPackFilePath(taskId);
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
            throw new BusinessException("分享链接不存在");
        }

        // 已手动取消的链接直接拒绝
        if (share.getStatus() == 0) {
            throw new BusinessException("分享链接已失效");
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
            throw new BusinessException("分享链接已失效");
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