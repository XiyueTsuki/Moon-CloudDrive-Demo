package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyCodeRequest {

    @NotBlank(message = "提取码不能为空")
    private String password;
}