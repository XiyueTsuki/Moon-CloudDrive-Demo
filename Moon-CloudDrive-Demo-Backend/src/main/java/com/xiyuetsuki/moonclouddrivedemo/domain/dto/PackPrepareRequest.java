package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 多文件打包下载请求参数
 * 前端勾选多个文件后提交，后端返回 taskId 供轮询进度
 */
@Data
public class PackPrepareRequest {

    /** 要打包的文件 ID 列表，不允许为空，单次最多 50 个 */
    @NotEmpty(message = "请至少选择一个文件")
    @Size(max = 50, message = "单次最多打包50个文件")
    private List<Long> fileIds;
}