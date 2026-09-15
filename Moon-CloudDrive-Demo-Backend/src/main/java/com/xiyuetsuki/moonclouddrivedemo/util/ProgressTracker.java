package com.xiyuetsuki.moonclouddrivedemo.util;

import com.aliyun.oss.model.PartETag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkMetaInfo;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChunkProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.UploadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProgressTracker {

    private static final String REDIS_KEY_PREFIX = "file:progress:";
    /** 分片上传元信息的Redis键前缀，存储ChunkMetaInfo的JSON */
    private static final String CHUNK_META_PREFIX = "file:chunk:meta:";
    /** 已完成分片序号的Redis Set键前缀 */
    private static final String CHUNK_PARTS_PREFIX = "file:chunk:parts:";
    /** 已完成分片ETag的Redis Hash键前缀，Key为partNumber，Value为etag */
    private static final String CHUNK_ETAGS_PREFIX = "file:chunk:etags:";
    private static final long TTL_MINUTES = 10;
    /** 分片上传数据的Redis有效期：24小时，给用户充足的时间窗口用于断点续传 */
    private static final long CHUNK_TTL_HOURS = 24;

    /** 打包下载进度的Redis键前缀 */
    private static final String PACK_PROGRESS_PREFIX = "pack:progress:";
    /** 打包下载进度数据保留时间：60分钟 */
    private static final long PACK_TTL_MINUTES = 60;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void update(String taskId, int percent, String status, String message) {
        /*
        创建上传信息实体 -> Jackson将上传信息实体序列化成JSON -> JSON存入Redis
         */

        UploadProgress progress = new UploadProgress(percent, status, message);
        try {
            String json = objectMapper.writeValueAsString(progress);
            stringRedisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + taskId, json, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("进度序列化失败: {}", taskId, e);
        }
    }

    public UploadProgress get(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + taskId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, UploadProgress.class);
        } catch (JsonProcessingException e) {
            log.error("进度反序列化失败: {}", taskId, e);
            return null;
        }
    }

    // ==================== 分片上传进度管理 ====================

    /**
     * 初始化分片上传元信息并存入Redis
     *
     * @param uploadId 上传任务标识
     * @param meta     包含文件信息、分片参数、OSS uploadId的元信息
     */
    public void initChunkMeta(String uploadId, ChunkMetaInfo meta) {
        try {
            String json = objectMapper.writeValueAsString(meta);
            stringRedisTemplate.opsForValue().set(
                    CHUNK_META_PREFIX + uploadId, json, CHUNK_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("分片元数据序列化失败: {}", uploadId, e);
        }
    }

    /**
     * 从Redis获取分片上传元信息
     *
     * @param uploadId 上传任务标识
     * @return 元信息，如果不存在或已过期返回null
     */
    public ChunkMetaInfo getChunkMeta(String uploadId) {
        String json = stringRedisTemplate.opsForValue().get(CHUNK_META_PREFIX + uploadId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChunkMetaInfo.class);
        } catch (JsonProcessingException e) {
            log.error("分片元数据反序列化失败: {}", uploadId, e);
            return null;
        }
    }

    /**
     * 标记一个分片上传完成
     * 同时将分片序号存入Set（用于进度查询），将ETag存入Hash（用于最终合并）
     *
     * @param uploadId   上传任务标识
     * @param partNumber 分片编号（从1开始）
     * @param etag       分片的OSS ETag
     */
    public void markChunkComplete(String uploadId, int partNumber, String etag) {
        String partsKey = CHUNK_PARTS_PREFIX + uploadId;
        String etagsKey = CHUNK_ETAGS_PREFIX + uploadId;
        stringRedisTemplate.opsForSet().add(partsKey, String.valueOf(partNumber));
        stringRedisTemplate.opsForHash().put(etagsKey, String.valueOf(partNumber), etag);
        // 续期：每次上传分片后刷新所有相关键的过期时间
        stringRedisTemplate.expire(partsKey, CHUNK_TTL_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.expire(etagsKey, CHUNK_TTL_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.expire(CHUNK_META_PREFIX + uploadId, CHUNK_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 查询分片上传进度
     * 从Redis Set中读取已完成的分片序号，计算完成百分比
     *
     * @param uploadId 上传任务标识
     * @return 进度信息，任务不存在返回null
     */
    public ChunkProgressResponse getChunkProgress(String uploadId) {

        /*
        查询分片上传进度

        从Redis中读取分片上传元信息 -> 从Redis中读取已上传分片集合 ->
        将String类型的Set处理为Integer类型Set -> 计算已上传分片百分比 -> 封装Response返回
         */

        ChunkMetaInfo meta = getChunkMeta(uploadId);
        if (meta == null) {
            return null;
        }
        Set<String> completedSet = stringRedisTemplate.opsForSet()
                .members(CHUNK_PARTS_PREFIX + uploadId);
        Set<Integer> completedParts = Collections.emptySet();
        if (completedSet != null) {
            completedParts = completedSet.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
        }
        int completedCount = completedParts.size();
        int percent = meta.getChunkCount() > 0
                ? completedCount * 100 / meta.getChunkCount() : 0;

        return new ChunkProgressResponse(uploadId, meta.getChunkCount(),
                completedCount, completedParts, percent);
    }

    /**
     * 获取所有已完成分片的ETag列表
     * 从Redis Hash中读取，用于调用OSS的completeMultipartUpload合并文件
     *
     * @param uploadId 上传任务标识
     * @return PartETag列表，用于OSS合并调用
     */
    public List<PartETag> getPartETags(String uploadId) {
        String etagsKey = CHUNK_ETAGS_PREFIX + uploadId;
        Set<Object> entries = stringRedisTemplate.opsForHash().entries(etagsKey).keySet();
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<PartETag> partETags = new ArrayList<>();
        for (Object key : entries) {
            int partNumber = Integer.parseInt((String) key);
            String etag = (String) stringRedisTemplate.opsForHash().get(etagsKey, key);
            partETags.add(new PartETag(partNumber, etag));
        }
        return partETags;
    }

    /**
     * 清理分片上传相关的所有Redis缓存
     * 在完成上传或取消上传后调用，释放内存空间
     *
     * @param uploadId 上传任务标识
     */
    public void cleanupChunk(String uploadId) {
        stringRedisTemplate.delete(CHUNK_META_PREFIX + uploadId);
        stringRedisTemplate.delete(CHUNK_PARTS_PREFIX + uploadId);
        stringRedisTemplate.delete(CHUNK_ETAGS_PREFIX + uploadId);
    }

    // ==================== 打包下载进度管理 ====================

    /**
     * 更新打包任务进度到 Redis，TTL 为 30 分钟（与 ZIP 文件清理时间一致）
     *
     * @param taskId   打包任务唯一标识
     * @param progress 进度对象（queued/processing/ready/failed）
     */
    public void updatePackProgress(String taskId, PackProgressResponse progress) {
        try {
            String json = objectMapper.writeValueAsString(progress);
            stringRedisTemplate.opsForValue().set(
                    PACK_PROGRESS_PREFIX + taskId, json, PACK_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("打包进度序列化失败: {}", taskId, e);
        }
    }

    /**
     * 从 Redis 查询打包任务进度，TTL 过期后返回 null
     *
     * @param taskId 打包任务唯一标识
     * @return 进度对象，过期或不存在返回 null
     */
    public PackProgressResponse getPackProgress(String taskId) {
        String json = stringRedisTemplate.opsForValue().get(PACK_PROGRESS_PREFIX + taskId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PackProgressResponse.class);
        } catch (JsonProcessingException e) {
            log.error("打包进度反序列化失败: {}", taskId, e);
            return null;
        }
    }

    public void deletePackProgress(String taskId) {
        stringRedisTemplate.delete(PACK_PROGRESS_PREFIX + taskId);
    }
}