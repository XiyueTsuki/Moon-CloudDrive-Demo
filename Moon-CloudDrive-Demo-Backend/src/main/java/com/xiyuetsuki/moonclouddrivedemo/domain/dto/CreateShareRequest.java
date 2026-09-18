package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateShareRequest {

    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    private String password;

    private Integer expireHours;

    private Integer maxDownloads;
}