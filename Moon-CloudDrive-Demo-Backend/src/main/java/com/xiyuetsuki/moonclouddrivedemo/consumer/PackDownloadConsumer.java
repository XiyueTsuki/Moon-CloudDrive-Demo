package com.xiyuetsuki.moonclouddrivedemo.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.domain.message.PackDownloadMessage;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.impl.PackDownloadServiceImpl;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class PackDownloadConsumer {

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;
    private final ProgressTracker progressTracker;
    private final DefaultMQProducer packDownloadProducer;
    private final ObjectMapper objectMapper;

    /** RocketMQ NameServer 地址 */
    @Value("${rocketmq.namesrv-addr}")
    private String namesrvAddr;

    /** 打包消费者组名 */
    @Value("${rocketmq.consumer.pack-download.group}")
    private String consumerGroup;

    /** 最大重试次数，超过后不再重试 */
    @Value("${rocketmq.consumer.pack-download.max-reconsume-times}")
    private int maxReconsumeTimes;

    /** RocketMQ Push 消费者实例，异步初始化 */
    private DefaultMQPushConsumer consumer;

    /** 消费者是否已启动标志，用于 @PreDestroy 安全关闭 */
    private volatile boolean started = false;

    /**
     * 异步初始化消费者，启动失败不阻塞应用启动
     * <p>
     * 监听 PACK_DOWNLOAD_TOPIC 的 prepare 标签，接收打包任务消息，
     * 依次从 OSS 下载文件并写入 ZIP 输出流，完成后发送延时清理消息。
     * <p>
     * 线程配置: 最小 2 线程、最大 4 线程，批量消息大小限制为 1。
     * RocketMQ 不可用时仅打印警告日志，其他功能正常运行
     */
    @PostConstruct
    public void init() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("正在连接 RocketMQ 打包消费者: group={}, namesrv={}", consumerGroup, namesrvAddr);
                consumer = new DefaultMQPushConsumer(consumerGroup);
                consumer.setNamesrvAddr(namesrvAddr);
                consumer.setMaxReconsumeTimes(maxReconsumeTimes);
                consumer.subscribe("PACK_DOWNLOAD_TOPIC", "prepare");
                consumer.setConsumeThreadMin(2);
                consumer.setConsumeThreadMax(4);
                consumer.setConsumeMessageBatchMaxSize(1);

                consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt msg : msgs) {
                        try {
                            PackDownloadMessage packMsg = objectMapper.readValue(
                                    msg.getBody(), PackDownloadMessage.class);
                            processPackTask(packMsg);
                        } catch (Exception e) {
                            log.error("处理打包消息失败: msgId={}", msg.getMsgId(), e);
                            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                });

                consumer.start();
                started = true;
                log.info("打包下载消费者启动成功: group={}", consumerGroup);
            } catch (Exception e) {
                log.warn("打包下载消费者启动失败（RocketMQ 可能未运行，打包下载功能将不可用）: {}", e.getMessage());
            }
        });
    }

    /**
     * 安全关闭消费者，仅在已启动状态下执行 shutdown
     */
    @PreDestroy
    public void destroy() {
        if (started && consumer != null) {
            consumer.shutdown();
            log.info("打包下载消费者已关闭");
        }
    }

    /**
     * 核心打包逻辑
     * <p>
     * 从 OSS 逐个下载文件并追加到同一个 ZIP 输出流中，避免内存中缓存全部文件。
     * 单个文件下载失败时跳过该文件继续打包其余文件，不中断整个任务。
     * 完成后更新 Redis 进度状态并发送延时清理消息。
     *
     * @param msg 打包消息体，包含 taskId、userId、fileIds
     * @throws RuntimeException 完整的打包流程失败时抛出，触发 RocketMQ 重试
     */
    private void processPackTask(PackDownloadMessage msg) {
        String taskId = msg.getTaskId();
        log.info("开始处理打包任务: taskId={}, fileCount={}", taskId, msg.getFileIds().size());

        String zipPath = PackDownloadServiceImpl.getTempZipPath(taskId);
        Path zipFile = Path.of(zipPath);

        try {
            Files.createDirectories(zipFile.getParent());

            progressTracker.updatePackProgress(taskId, PackProgressResponse.processing(0, "开始打包"));

            int total = msg.getFileIds().size();
            Set<String> usedNames = new HashSet<>();

            try (FileOutputStream fos = new FileOutputStream(zipFile.toFile());
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                for (int i = 0; i < total; i++) {
                    Long fileId = msg.getFileIds().get(i);
                    File file = fileMapper.selectByUserIdAndId(msg.getUserId(), fileId);

                    if (file == null) {
                        log.warn("打包时文件不存在，跳过: fileId={}", fileId);
                        int percent = (i + 1) * 100 / total;
                        progressTracker.updatePackProgress(taskId,
                                PackProgressResponse.processing(percent, "打包中 (" + (i + 1) + "/" + total + ")"));
                        continue;
                    }

                    String entryName = resolveFileName(file.getOriginalFilename(), usedNames);
                    usedNames.add(entryName);

                    com.aliyun.oss.model.OSSObject ossObject = null;
                    try {
                        ossObject = ossUtil.getObject(file.getStoredFilename());
                        try (InputStream is = ossObject.getObjectContent()) {
                            ZipEntry entry = new ZipEntry(entryName);
                            entry.setSize(file.getFileSize() != null ? file.getFileSize() : 0);
                            zos.putNextEntry(entry);

                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                zos.write(buffer, 0, len);
                            }
                            zos.closeEntry();
                        }
                    } catch (Exception e) {
                        log.warn("OSS下载失败，跳过文件: fileId={}, storedFilename={}, error={}",
                                fileId, file.getStoredFilename(), e.getMessage());
                        usedNames.remove(entryName);
                    } finally {
                        if (ossObject != null) {
                            try {
                                ossObject.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }

                    int percent = (i + 1) * 100 / total;
                    progressTracker.updatePackProgress(taskId,
                            PackProgressResponse.processing(percent, "打包中 (" + (i + 1) + "/" + total + ")"));
                    log.debug("打包进度: taskId={}, file={}, {}/{}", taskId, entryName, i + 1, total);
                }
            }

            String zipFilename = "pack_" + taskId.substring(0, 8) + ".zip";
            progressTracker.updatePackProgress(taskId, PackProgressResponse.ready(zipFilename));
            log.info("打包完成: taskId={}, zipPath={}, size={} bytes",
                    taskId, zipPath, Files.size(zipFile));

            sendCleanupMessage(taskId, zipPath);

        } catch (Exception e) {
            log.error("打包任务失败: taskId={}", taskId, e);
            progressTracker.updatePackProgress(taskId,
                    PackProgressResponse.failed("打包失败: " + e.getMessage()));
            try {
                Files.deleteIfExists(zipFile);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("打包任务失败", e);
        }
    }

    /**
     * 处理同名冲突文件名，为重复名称添加自增后缀（如 a.txt → a_1.txt）
     *
     * @param originalName 原始文件名
     * @param usedNames    已使用的 ZIP Entry 名称集合
     * @return 去重后的文件名
     */
    private String resolveFileName(String originalName, Set<String> usedNames) {
        if (!usedNames.contains(originalName)) {
            return originalName;
        }
        int dotIndex = originalName.lastIndexOf('.');
        String baseName;
        String ext;
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            ext = originalName.substring(dotIndex);
        } else {
            baseName = originalName;
            ext = "";
        }
        int counter = 1;
        String candidate;
        do {
            candidate = baseName + "_" + counter + ext;
            counter++;
        } while (usedNames.contains(candidate));
        return candidate;
    }

    /**
     * 发送延时清理消息，30 分钟后自动删除临时 ZIP 文件
     * <p>
     * 延时级别 16 = 30 分钟（RocketMQ 默认 18 级，1s ~ 2h），给用户足够时间下载。
     * 消息发送失败不影响打包结果，仅记录日志。
     *
     * @param taskId  打包任务 ID
     * @param zipPath 临时 ZIP 文件路径
     */
    private void sendCleanupMessage(String taskId, String zipPath) {
        try {
            String body = objectMapper.writeValueAsString(
                    new CleanupMessage(taskId, zipPath));
            Message message = new Message("PACK_DOWNLOAD_TOPIC", "cleanup", body.getBytes(StandardCharsets.UTF_8));
            message.setDelayTimeLevel(16);
            packDownloadProducer.send(message);
            log.info("已发送延时清理消息: taskId={}, delayLevel=16(30min)", taskId);
        } catch (Exception e) {
            log.error("发送清理消息失败: taskId={}", taskId, e);
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class CleanupMessage {
        private String taskId;
        private String zipPath;
    }
}