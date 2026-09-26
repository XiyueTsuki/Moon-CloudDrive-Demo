package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;

/**
 * 打包下载服务接口，负责多文件 ZIP 打包的提交、进度查询和文件获取
 */
public interface PackDownloadService {

    /**
     * 提交打包下载任务（需登录认证）
     * 校验文件归属后发送 RocketMQ 消息，返回 taskId 供前端轮询
     *
     * @param fileIds 要打包的文件ID列表
     * @return 任务标识
     */
    String preparePack(java.util.List<Long> fileIds);

    /**
     * 提交打包任务（不校验用户权限，由调用方进行权限控制）
     * 用于分享文件夹下载等无需登录认证的场景
     *
     * @param userId  文件拥有者用户ID
     * @param fileIds 要打包的文件ID列表
     * @return 任务标识
     */
    String submitPackTask(Long userId, java.util.List<Long> fileIds);

    /**
     * 查询打包任务的实时进度
     *
     * @param taskId 任务标识
     * @return 进度信息，任务不存在返回 null
     */
    PackProgressResponse getPackProgress(String taskId);

    /**
     * 获取已就绪的 ZIP 临时文件路径
     *
     * @param taskId 任务标识
     * @return ZIP 文件绝对路径，未就绪或不存在返回 null
     */
    String getPackFilePath(String taskId);
}