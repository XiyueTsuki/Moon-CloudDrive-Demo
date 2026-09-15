package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 多文件打包下载请求参数
 * 前端勾选多个文件后提交，后端返回 taskId 供轮询进度
 */
@Data
public class PackPrepareRequest {

    /** 要打包的文件 ID 列表，不允许为空，单次最多 50 个 */
    private List<Long> fileIds;
}