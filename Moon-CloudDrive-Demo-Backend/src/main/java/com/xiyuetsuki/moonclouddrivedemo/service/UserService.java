package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;

public interface UserService {

    void sendVerifyCode(String email);

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}