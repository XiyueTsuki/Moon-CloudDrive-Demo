package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChangePasswordRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;

public interface UserService {

    /** 发送邮箱验证码 */
    void sendVerifyCode(String email);

    /** 用户注册 */
    void register(RegisterRequest request);

    /** 用户登录 */
    LoginResponse login(LoginRequest request);

    /** 修改密码 */
    void changePassword(ChangePasswordRequest request);
}