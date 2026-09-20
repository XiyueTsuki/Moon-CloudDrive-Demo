package com.xiyuetsuki.moonclouddrivedemo.config.actuator;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.xiyuetsuki.moonclouddrivedemo.config.OssConfig;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OssHealthIndicator implements HealthIndicator {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    public OssHealthIndicator(OSS ossClient, OssConfig ossConfig) {
        this.ossClient = ossClient;
        this.ossConfig = ossConfig;
    }

    @Override
    public Health health() {
        try {
            boolean exists = ossClient.doesBucketExist(ossConfig.getBucketName());
            if (exists) {
                return Health.up()
                        .withDetail("bucket", ossConfig.getBucketName())
                        .withDetail("endpoint", ossConfig.getEndpoint())
                        .build();
            }
            return Health.down()
                    .withDetail("bucket", ossConfig.getBucketName())
                    .withDetail("reason", "bucket 不存在")
                    .build();
        } catch (OSSException e) {
            String msg = e.getErrorCode().equals(OSSErrorCode.ACCESS_DENIED)
                    ? "无访问权限，请检查 AccessKey 配置" : e.getMessage();
            return Health.down()
                    .withDetail("bucket", ossConfig.getBucketName())
                    .withDetail("endpoint", ossConfig.getEndpoint())
                    .withDetail("errorCode", e.getErrorCode())
                    .withDetail("reason", msg)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("bucket", ossConfig.getBucketName())
                    .withDetail("endpoint", ossConfig.getEndpoint())
                    .withDetail("reason", "无法连接 OSS: " + e.getMessage())
                    .build();
        }
    }
}