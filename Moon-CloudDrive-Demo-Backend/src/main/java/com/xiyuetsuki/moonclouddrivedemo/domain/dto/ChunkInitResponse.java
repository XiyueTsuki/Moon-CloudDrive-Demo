package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传初始化响应
 * 返回上传任务标识、分片参数以及秒传结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkInitResponse {

    /** 本次上传任务的唯一标识 */
    private String uploadId;

    /** 总的分片数量 */
    private int chunkCount;

    /** 每个分片的大小（字节） */
    private long chunkSize;

    /** 是否秒传成功（文件哈希已存在，无需真正上传） */
    private boolean instantComplete;

    /** 秒传成功时返回的已有文件信息 */
    private FileVO file;

    /**
     * 构造一个秒传成功的响应（跳过上传流程）
     *
     * @param file 已存在的文件记录
     * @return 标记为秒传完成的响应
     */
    public static ChunkInitResponse instant(FileVO file) {
        ChunkInitResponse resp = new ChunkInitResponse();
        resp.setInstantComplete(true);
        resp.setFile(file);
        return resp;
    }
}