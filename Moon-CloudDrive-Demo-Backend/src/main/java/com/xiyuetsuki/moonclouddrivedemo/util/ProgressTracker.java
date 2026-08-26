package com.xiyuetsuki.moonclouddrivedemo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.UploadProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProgressTracker {

    private static final String REDIS_KEY_PREFIX = "file:progress:";
    private static final long TTL_MINUTES = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void update(String taskId, int percent, String status, String message) {
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
}