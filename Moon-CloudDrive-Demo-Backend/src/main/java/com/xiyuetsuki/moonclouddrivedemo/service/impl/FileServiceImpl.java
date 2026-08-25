package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;

    /**
     * 文件上传
     * @param file
     */
    @Override
    public void uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        try {
            //去重秒传
            byte[] fileBytes = file.getBytes();
            String fileHash = computeSha256(fileBytes);

            File existingFile = fileMapper.selectByFileHash(fileHash);
            if (existingFile != null) {
                File fileRecord = new File();
                fileRecord.setOriginalFilename(originalFilename);
                fileRecord.setStoredFilename(existingFile.getStoredFilename());
                fileRecord.setFileSize(file.getSize());
                fileRecord.setContentType(file.getContentType());
                fileRecord.setFileHash(fileHash);
                fileRecord.setOssUrl(existingFile.getOssUrl());
                fileRecord.setUploadTime(LocalDateTime.now());

                fileMapper.insert(fileRecord);

                log.info("文件秒传成功(复用已有文件): {} -> {}", originalFilename, existingFile.getOssUrl());
                return;
            }

            //正常上传
            try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                String storedFilename = ossUtil.upload(inputStream, originalFilename);
                String ossUrl = ossUtil.getOssUrl(storedFilename);

                File fileRecord = new File();
                fileRecord.setOriginalFilename(originalFilename);
                fileRecord.setStoredFilename(storedFilename);
                fileRecord.setFileSize(file.getSize());
                fileRecord.setContentType(file.getContentType());
                fileRecord.setFileHash(fileHash);
                fileRecord.setOssUrl(ossUrl);
                fileRecord.setUploadTime(LocalDateTime.now());

                fileMapper.insert(fileRecord);

                log.info("文件上传成功: {} -> {}", originalFilename, ossUrl);
            }
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalFilename, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 计算SHA-256哈希值
     * @param data
     * @return
     */
    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }
}