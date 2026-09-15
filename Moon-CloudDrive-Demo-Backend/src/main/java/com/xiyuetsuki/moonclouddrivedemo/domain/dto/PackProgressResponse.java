package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 打包任务进度响应
 * <p>
 * 状态流转: queued(已入队) → processing(打包中) → ready(完成) | failed(失败)
 * <p>
 * 前端通过 redisKey(taskId) 轮询 GET /api/file/pack/progress?taskId=xxx 获取实时进度
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackProgressResponse {

    /** 状态：queued / processing / ready / failed */
    private String status;

    /** 进度百分比 0-100 */
    private int percent;

    /** 状态描述 */
    private String message;

    /** ZIP 就绪后的文件名（仅 status=ready 时有值） */
    private String zipFilename;

    public static PackProgressResponse queued() {
        return new PackProgressResponse("queued", 0, "任务已加入队列，等待处理", null);
    }

    public static PackProgressResponse processing(int percent, String message) {
        return new PackProgressResponse("processing", percent, message, null);
    }

    public static PackProgressResponse ready(String zipFilename) {
        return new PackProgressResponse("ready", 100, "打包完成", zipFilename);
    }

    public static PackProgressResponse failed(String message) {
        return new PackProgressResponse("failed", 0, message, null);
    }
}