package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChangePasswordRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.SendCodeRequest;
import com.xiyuetsuki.moonclouddrivedemo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理", description = "用户注册、登录、发送验证码、修改密码等接口")
@RestController
@RequestMapping("/api/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "发送验证码", description = "向指定邮箱发送验证码，用于注册流程")
    @PostMapping("/send-code")
    public Response<Void> sendVerifyCode(@Valid @RequestBody SendCodeRequest request) {
        userService.sendVerifyCode(request.getEmail());
        return Response.ok("验证码已发送");
    }

    @Operation(summary = "用户注册", description = "使用邮箱和验证码完成注册")
    @PostMapping("/register")
    public Response<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Response.ok("注册成功");
    }

    @Operation(summary = "用户登录", description = "使用邮箱和密码登录，返回Token")
    @PostMapping("/login")
    public Response<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse loginResponse = userService.login(request);
        return Response.ok(loginResponse, "登录成功");
    }

    @Operation(summary = "修改密码", description = "用户登录后提交旧密码和新密码进行修改")
    @PostMapping("/change-password")
    public Response<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Response.ok("密码修改成功");
    }
}