package com.xiyuetsuki.moonclouddrivedemo.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.event.ProgressEvent;
import com.aliyun.oss.event.ProgressEventType;
import com.aliyun.oss.event.ProgressListener;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.PutObjectRequest;
import com.xiyuetsuki.moonclouddrivedemo.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OssUtil {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    public String upload(InputStream inputStream, String originalFilename,
            Consumer<Double> progressCallback) {
        String storedFilename = generateStoredFilename(originalFilename);

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossConfig.getBucketName(),
                storedFilename,
                inputStream
        );

        if (progressCallback != null) {
            putObjectRequest.withProgressListener(new ProgressListener() {
                private long bytesWritten = 0;

                @Override
                public void progressChanged(ProgressEvent event) {
                    if (event.getEventType() == ProgressEventType.REQUEST_BYTE_TRANSFER_EVENT) {
                        bytesWritten += event.getBytes();
                        progressCallback.accept((double) bytesWritten);
                    }
                }
            });
        }

        ossClient.putObject(putObjectRequest);

        log.info("OSS上传成功: {} -> {}", originalFilename, storedFilename);
        return storedFilename;
    }

    public String upload(InputStream inputStream, String originalFilename) {
        return upload(inputStream, originalFilename, null);
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

    public String generatePresignedUrl(String storedFilename, String originalFilename) {
        Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000);

        String encodedFilename = java.net.URLEncoder.encode(originalFilename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossConfig.getBucketName(), storedFilename);
        request.setExpiration(expiration);
        request.addQueryParameter("response-content-disposition",
                "attachment;filename=" + encodedFilename);

        URL url = ossClient.generatePresignedUrl(request);
        return url.toString();
    }
}