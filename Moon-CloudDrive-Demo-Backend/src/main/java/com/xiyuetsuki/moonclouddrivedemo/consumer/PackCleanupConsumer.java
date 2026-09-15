package com.xiyuetsuki.moonclouddrivedemo.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class PackCleanupConsumer {

    /** RocketMQ NameServer 地址 */
    @Value("${rocketmq.namesrv-addr}")
    private String namesrvAddr;

    /** 清理消费者组名 */
    @Value("${rocketmq.consumer.pack-cleanup.group}")
    private String consumerGroup;

    /** RocketMQ Push 消费者实例，异步初始化 */
    private DefaultMQPushConsumer consumer;

    /** 消费者是否已启动标志，用于 @PreDestroy 安全关闭 */
    private volatile boolean started = false;

    /**
     * 异步初始化消费者，启动失败不阻塞应用启动
     * <p>
     * 监听 PACK_DOWNLOAD_TOPIC 的 cleanup 标签，接收完成打包后发送的延时消息，
     * 在 ZIP 文件被下载或超时后删除临时文件，防止磁盘空间泄漏。
     * <p>
     * RocketMQ 不可用时仅打印警告日志，其他功能正常运行
     */
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("正在连接 RocketMQ 清理消费者: group={}, namesrv={}", consumerGroup, namesrvAddr);
                consumer = new DefaultMQPushConsumer(consumerGroup);
                consumer.setNamesrvAddr(namesrvAddr);
                consumer.setMaxReconsumeTimes(2);
                consumer.subscribe("PACK_DOWNLOAD_TOPIC", "cleanup");

                consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt msg : msgs) {
                        try {
                            String body = new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                            log.info("收到清理消息: {}", body);

                            String zipPath = extractField(body, "zipPath");
                            if (zipPath != null) {
                                Path path = Path.of(zipPath);
                                boolean deleted = Files.deleteIfExists(path);
                                log.info("临时ZIP清理{}: {}", deleted ? "成功" : "跳过（文件不存在）", zipPath);
                            }
                        } catch (Exception e) {
                            log.error("清理任务失败: msgId={}", msg.getMsgId(), e);
                            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });

                consumer.start();
                started = true;
                log.info("打包清理消费者启动成功: group={}", consumerGroup);
            } catch (Exception e) {
                log.warn("打包清理消费者启动失败（RocketMQ 可能未运行，打包下载功能将不可用）: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        if (started && consumer != null) {
            consumer.shutdown();
            log.info("打包清理消费者已关闭");
        }
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
}