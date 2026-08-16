package com.xiyuetsuki.moonclouddrivedemo.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.xiyuetsuki.moonclouddrivedemo.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OssUtil {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    public String upload(InputStream inputStream, String originalFilename) {
        String storedFilename = generateStoredFilename(originalFilename);

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossConfig.getBucketName(),
                storedFilename,
                inputStream
        );
        ossClient.putObject(putObjectRequest);

        log.info("OSS上传成功: {} -> {}", originalFilename, storedFilename);
        return storedFilename;
    }

    public String generateStoredFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    public String getOssUrl(String storedFilename) {
        return String.format("https://%s.%s/%s",
                ossConfig.getBucketName(),
                ossConfig.getEndpoint(),
                storedFilename);
    }
}