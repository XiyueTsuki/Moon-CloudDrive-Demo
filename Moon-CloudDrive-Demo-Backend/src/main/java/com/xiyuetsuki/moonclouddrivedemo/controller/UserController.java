package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.SendCodeRequest;
import com.xiyuetsuki.moonclouddrivedemo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 发送验证码
     *
     * @param request
     * @return
     */
    @PostMapping("/send-code")
    public Response<Void> sendVerifyCode(@RequestBody SendCodeRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Response.bad(400, "邮箱不能为空");
        }

        userService.sendVerifyCode(request.getEmail());

        return Response.ok("验证码已发送");
    }

    /**
     * 用户注册
     *
     * @param request
     * @return
     */
    @PostMapping("/register")
    public Response<Void> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return Response.bad(400, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return Response.bad(400, "密码不能为空");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Response.bad(400, "邮箱不能为空");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            return Response.bad(400, "验证码不能为空");
        }

        userService.register(request);

        return Response.ok("注册成功");
    }

    /**
     * 用户登陆
     *
     * @param request
     * @return
     */
    @PostMapping("/login")
    public Response<LoginResponse> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Response.bad(400, null, "邮箱不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return Response.bad(400, null, "密码不能为空");
        }

        LoginResponse loginResponse = userService.login(request);

        return Response.ok(loginResponse, "登录成功");
    }
}