package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.mapper.ShareMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.ShareExpireManager;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ShareExpireManagerImpl implements ShareExpireManager {

    private static final String QUEUE_NAME = "share:expire:queue";

    private final RBlockingQueue<String> blockingQueue;
    private final RDelayedQueue<String> delayedQueue;
    private final ShareMapper shareMapper;

    public ShareExpireManagerImpl(RedissonClient redissonClient, ShareMapper shareMapper) {
        this.blockingQueue = redissonClient.getBlockingQueue(QUEUE_NAME);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        this.shareMapper = shareMapper;
    }

    @Override
    public void schedule(String shareCode, LocalDateTime expireTime) {
        long delay = ChronoUnit.SECONDS.between(LocalDateTime.now(), expireTime);
        if (delay > 0) {
            delayedQueue.offer(shareCode, delay, TimeUnit.SECONDS);
            log.info("分享链接已加入延迟队列: code={}, delay={}s", shareCode, delay);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        reloadFromDatabase();
        startConsumer();
    }

    private void reloadFromDatabase() {
        List<Share> unexpired = shareMapper.selectUnexpired();
        for (Share share : unexpired) {
            long delay = ChronoUnit.SECONDS.between(LocalDateTime.now(), share.getExpireTime());
            if (delay > 0) {
                delayedQueue.offer(share.getShareCode(), delay, TimeUnit.SECONDS);
            }
        }
        log.info("启动时从数据库重载未过期分享链接: {} 条", unexpired.size());
    }

    private void startConsumer() {
        Thread consumer = new Thread(() -> {
            log.info("分享链接过期消费线程已启动");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String shareCode = blockingQueue.take();
                    Share share = shareMapper.selectByShareCode(shareCode);
                    if (share != null && share.getStatus() == 1) {
                        share.setStatus(0);
                        shareMapper.updateById(share);
                        log.info("分享链接已自动过期: code={}", shareCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("处理过期分享链接异常", e);
                }
            }
        }, "share-expire-consumer");
        consumer.setDaemon(true);
        consumer.start();
    }
}