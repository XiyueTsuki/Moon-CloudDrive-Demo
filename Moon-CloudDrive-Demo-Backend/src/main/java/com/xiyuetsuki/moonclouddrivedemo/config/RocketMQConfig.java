package com.xiyuetsuki.moonclouddrivedemo.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 生产者配置
 * <p>
 * 创建并启动打包下载消息的生产者 Bean，启动失败时仅记录警告日志不中断应用。
 * 消费者由 {@link com.xiyuetsuki.moonclouddrivedemo.consumer.PackDownloadConsumer} 和
 * {@link com.xiyuetsuki.moonclouddrivedemo.consumer.PackCleanupConsumer} 自行管理。
 */
@Slf4j
@Configuration
public class RocketMQConfig {

    /** RocketMQ NameServer 地址 */
    @Value("${rocketmq.namesrv-addr}")
    private String namesrvAddr;

    /** 生产者组名，用于在 RocketMQ 控制台中识别 */
    @Value("${rocketmq.producer.group}")
    private String producerGroup;

    /**
     * 创建打包下载消息生产者
     * <p>
     * 发送超时 3 秒，同步发送失败最多重试 2 次。
     * 如果 RocketMQ 不可用则跳过启动，应用其余功能不受影响。
     */
    @Bean("packDownloadProducer")
    public DefaultMQProducer packDownloadProducer() {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(namesrvAddr);
        producer.setSendMsgTimeout(3000);
        producer.setRetryTimesWhenSendFailed(2);
        try {
            producer.start();
            log.info("RocketMQ Producer 启动成功: group={}, namesrv={}", producerGroup, namesrvAddr);
        } catch (Exception e) {
            log.warn("RocketMQ Producer 启动失败（RocketMQ 可能未运行，打包下载功能将不可用）: {}", e.getMessage());
        }
        return producer;
    }
}