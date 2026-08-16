package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;

    @Override
    public void uploadFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();

        try (InputStream inputStream = file.getInputStream()) {
            String storedFilename = ossUtil.upload(inputStream, originalFilename);
            String ossUrl = ossUtil.getOssUrl(storedFilename);

            File fileRecord = new File();
            fileRecord.setOriginalFilename(originalFilename);
            fileRecord.setStoredFilename(storedFilename);
            fileRecord.setFileSize(file.getSize());
            fileRecord.setContentType(file.getContentType());
            fileRecord.setOssUrl(ossUrl);
            fileRecord.setUploadTime(LocalDateTime.now());

            fileMapper.insert(fileRecord);

            log.info("文件上传成功: {} -> {}", originalFilename, ossUrl);
        } catch (IOException e) {
            log.error("文件上传失败: {}", originalFilename, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }
}