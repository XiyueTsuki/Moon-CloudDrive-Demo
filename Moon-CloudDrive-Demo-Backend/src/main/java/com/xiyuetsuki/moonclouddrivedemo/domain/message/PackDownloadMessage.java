package com.xiyuetsuki.moonclouddrivedemo.domain.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RocketMQ 打包下载消息体
 * <p>
 * Producer 在 prepare 阶段将任务序列化为 JSON 发送到 PACK_DOWNLOAD_TOPIC，
 * Consumer 反序列化后依次从 OSS 拉取文件、打包为 ZIP
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackDownloadMessage {

    /** 打包任务唯一标识（UUID），用于 Redis 进度追踪 */
    private String taskId;

    /** 发起打包的用户 ID，用于权限校验和文件 Owner 匹配 */
    private Long userId;

    /** 需要打包的文件 ID 列表，Consumer 逐个从数据库查询后从 OSS 拉取 */
    private List<Long> fileIds;
}