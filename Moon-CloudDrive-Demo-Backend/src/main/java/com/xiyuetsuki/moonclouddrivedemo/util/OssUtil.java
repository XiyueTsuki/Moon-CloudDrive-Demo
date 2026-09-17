package com.xiyuetsuki.moonclouddrivedemo.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.event.ProgressEvent;
import com.aliyun.oss.event.ProgressEventType;
import com.aliyun.oss.event.ProgressListener;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.xiyuetsuki.moonclouddrivedemo.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OssUtil {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    public String upload(InputStream inputStream, String originalFilename,
            String contentType, Consumer<Double> progressCallback) {
        String storedFilename = generateStoredFilename(originalFilename);

        ObjectMetadata metadata = new ObjectMetadata();
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        metadata.setContentDisposition("inline");

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossConfig.getBucketName(),
                storedFilename,
                inputStream,
                metadata
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

    public String upload(InputStream inputStream, String originalFilename, String contentType) {
        return upload(inputStream, originalFilename, contentType, null);
    }

    /**
     * 以指定的 OSS key 直接上传（不做文件名转换），适用于需要精确控制 OSS 路径的场景。
     *
     * @param inputStream 文件输入流
     * @param ossKey      OSS 存储键（完整路径）
     * @param contentType 文件内容类型
     */
    public void uploadWithKey(InputStream inputStream, String ossKey, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        metadata.setContentDisposition("inline");

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossConfig.getBucketName(), ossKey, inputStream, metadata);
        ossClient.putObject(putObjectRequest);
        log.debug("OSS上传成功(by key): {}", ossKey);
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

        ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
        headers.setContentDisposition("attachment;filename=" + encodedFilename);
        request.setResponseHeaders(headers);

        URL url = ossClient.generatePresignedUrl(request);
        return url.toString().replace("http://", "https://");
    }

    /**
     * 从 OSS 中删除指定文件
     *
     * @param storedFilename 文件在OSS中的存储名称
     */
    public void deleteFile(String storedFilename) {
        ossClient.deleteObject(ossConfig.getBucketName(), storedFilename);
        log.info("OSS文件删除成功: {}", storedFilename);
    }

    // ==================== 分片上传相关方法 ====================

    /**
     * 初始化阿里云OSS多段上传任务
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @return OSS返回的uploadId，用于后续分片上传和合并
     */
    public String initiateMultipartUpload(String storedFilename, String contentType) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                ossConfig.getBucketName(), storedFilename);

        ObjectMetadata metadata = new ObjectMetadata();
        if (contentType != null && !contentType.isEmpty()) {
            metadata.setContentType(contentType);
        }
        metadata.setContentDisposition("inline");
        request.setObjectMetadata(metadata);

        InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
        log.info("OSS分片上传初始化: {} -> uploadId={}", storedFilename, result.getUploadId());
        return result.getUploadId();
    }

    /**
     * 上传单个分片至OSS
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @param ossUploadId    OSS多段上传任务标识
     * @param partNumber     分片编号（从1开始）
     * @param chunkData      分片数据输入流
     * @param chunkSize      分片大小（字节）
     * @return 分片的ETag，用于最终合并时校验
     */
    public PartETag uploadPart(String storedFilename, String ossUploadId,
            int partNumber, InputStream chunkData, long chunkSize) {
        UploadPartRequest uploadPartRequest = new UploadPartRequest();
        uploadPartRequest.setBucketName(ossConfig.getBucketName());
        uploadPartRequest.setKey(storedFilename);
        uploadPartRequest.setUploadId(ossUploadId);
        uploadPartRequest.setPartNumber(partNumber);
        uploadPartRequest.setInputStream(chunkData);
        uploadPartRequest.setPartSize(chunkSize);

        UploadPartResult result = ossClient.uploadPart(uploadPartRequest);
        log.debug("OSS分片上传: {} part={} etag={}", storedFilename, partNumber, result.getPartETag().getETag());
        return result.getPartETag();
    }

    /**
     * 完成OSS多段上传，合并所有分片为完整文件
     * 会自动按分片编号排序后提交给OSS
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @param ossUploadId    OSS多段上传任务标识
     * @param partETags      所有分片的ETag列表
     */
    public void completeMultipartUpload(String storedFilename, String ossUploadId,
            List<PartETag> partETags) {
        partETags.sort((a, b) -> Integer.compare(a.getPartNumber(), b.getPartNumber()));
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                ossConfig.getBucketName(), storedFilename, ossUploadId, partETags);
        ossClient.completeMultipartUpload(request);
        log.info("OSS分片上传合并完成: {}", storedFilename);
    }

    /**
     * 中止OSS多段上传，释放已上传的碎片
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @param ossUploadId    OSS多段上传任务标识
     */
    public void abortMultipartUpload(String storedFilename, String ossUploadId) {
        ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(
                ossConfig.getBucketName(), storedFilename, ossUploadId));
        log.info("OSS分片上传已取消: {} uploadId={}", storedFilename, ossUploadId);
    }

    /**
     * 查询OSS中已上传的分片列表
     * 返回的PartSummary会转换为PartETag以便直接用于合并调用
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @param ossUploadId    OSS多段上传任务标识
     * @return 已上传分片的ETag列表
     */
    public List<PartETag> listParts(String storedFilename, String ossUploadId) {
        return ossClient.listParts(new ListPartsRequest(
                ossConfig.getBucketName(), storedFilename, ossUploadId))
                .getParts()
                .stream()
                .map(p -> new PartETag(p.getPartNumber(), p.getETag()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 从 OSS 下载文件并返回 OSSObject
     * 调用方需自行关闭 OSSObject 释放连接
     *
     * @param storedFilename 文件在OSS中的存储名称
     * @return OSSObject，包含文件内容流和元信息
     */
    public OSSObject getObject(String storedFilename) {
        return ossClient.getObject(ossConfig.getBucketName(), storedFilename);
    }

    /**
     * 获取 Bucket 名称
     */
    public String getBucketName() {
        return ossConfig.getBucketName();
    }

    // ==================== 文件预览相关方法 ====================

    /**
     * 生成 OSS 预签名 URL（用于在线预览）
     * <p>
     * 文件的实际 Content-Type 在上传时已写入 OSS 对象元数据，预签名 URL 无需额外设置。
     * 浏览器根据 Content-Type 自动决定渲染方式（img/video/audio/pdf 内嵌展示）。
     * 有效期默认 30 分钟。
     *
     * @param storedFilename 文件在 OSS 中的存储名称
     * @return 预签名 URL，可直接作为 img src / video src / iframe src 使用
     */
    public String generatePresignedUrlForPreview(String storedFilename) {
        return generatePresignedUrlForPreview(storedFilename, 30, TimeUnit.MINUTES);
    }

    /**
     * 生成 OSS 预签名 URL（用于在线预览），自定义有效期
     */
    public String generatePresignedUrlForPreview(String storedFilename, long duration, TimeUnit unit) {
        Date expiration = new Date(System.currentTimeMillis() + unit.toMillis(duration));

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossConfig.getBucketName(), storedFilename);
        request.setExpiration(expiration);

        URL url = ossClient.generatePresignedUrl(request);
        return url.toString().replace("http://", "https://");
    }
}