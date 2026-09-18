package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO
 * 前端提交旧密码和新密码，后端校验旧密码正确后更新密码
 */
@Data
public class ChangePasswordRequest {

    /** 用户当前密码，用于身份校验 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 用户新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码长度不能少于6位")
    private String newPassword;
}