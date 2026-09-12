package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

/**
 * 修改密码请求 DTO
 * 前端提交旧密码和新密码，后端校验旧密码正确后更新密码
 */
@Data
public class ChangePasswordRequest {

    /** 用户当前密码，用于身份校验 */
    private String oldPassword;

    /** 用户新密码 */
    private String newPassword;
}