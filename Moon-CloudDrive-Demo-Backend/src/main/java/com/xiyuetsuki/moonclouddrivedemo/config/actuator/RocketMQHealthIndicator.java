package com.xiyuetsuki.moonclouddrivedemo.config.actuator;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class RocketMQHealthIndicator implements HealthIndicator {

    private final DefaultMQProducer producer;
    private final String namesrvAddr;

    public RocketMQHealthIndicator(
            @Qualifier("packDownloadProducer") DefaultMQProducer producer,
            @Value("${rocketmq.namesrv-addr}") String namesrvAddr) {
        this.producer = producer;
        this.namesrvAddr = namesrvAddr;
    }

    @Override
    public Health health() {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            return Health.down()
                    .withDetail("reason", "NameServer 地址未配置")
                    .build();
        }

        String host;
        int port;
        try {
            String[] parts = namesrvAddr.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return Health.down()
                    .withDetail("namesrvAddr", namesrvAddr)
                    .withDetail("reason", "NameServer 地址格式错误")
                    .build();
        }

        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }

        if (reachable) {
            return Health.up()
                    .withDetail("namesrvAddr", namesrvAddr)
                    .withDetail("producerGroup", producer.getProducerGroup())
                    .build();
        }
        return Health.down()
                .withDetail("namesrvAddr", namesrvAddr)
                .withDetail("reason", "NameServer 不可达")
                .build();
    }
}