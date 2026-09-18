package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.exception.handler.GlobalExceptionHandler;
import com.xiyuetsuki.moonclouddrivedemo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 测试 —— MockMvc standalone 模式
 * 零 Spring 上下文，不受 Sa-Token 拦截器影响，@Valid 校验仍然生效
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 注册测试 ====================

    @Test
    void register_validRequest_shouldReturn200() throws Exception {
        RegisterRequest req = buildRegisterRequest("user1", "123456", "a@b.com", "888888");
        doNothing().when(userService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("注册成功"));

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void register_invalidEmailFormat_shouldReturn400() throws Exception {
        RegisterRequest req = buildRegisterRequest("user1", "123456", "not-an-email", "888888");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.containsString("邮箱格式不正确")));
    }

    @Test
    void register_blankUsername_shouldReturn400() throws Exception {
        RegisterRequest req = buildRegisterRequest("", "123456", "a@b.com", "888888");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.containsString("用户名不能为空")));
    }

    @Test
    void register_shortPassword_shouldReturn400() throws Exception {
        RegisterRequest req = buildRegisterRequest("user1", "123", "a@b.com", "888888");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.containsString("密码长度不能少于6位")));
    }

    // ==================== 登录测试 ====================

    @Test
    void login_validRequest_shouldReturn200AndToken() throws Exception {
        LoginRequest req = buildLoginRequest("a@b.com", "123456");
        LoginResponse resp = new LoginResponse("token-abc", "user1", "a@b.com");
        when(userService.login(any(LoginRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("token-abc"))
                .andExpect(jsonPath("$.data.username").value("user1"));

        verify(userService).login(any(LoginRequest.class));
    }

    @Test
    void login_blankEmail_shouldReturn400() throws Exception {
        LoginRequest req = buildLoginRequest("", "123456");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.containsString("邮箱不能为空")));
    }

    // ==================== 辅助构建方法 ====================

    private RegisterRequest buildRegisterRequest(String username, String password, String email, String code) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setEmail(email);
        req.setCode(code);
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }
}