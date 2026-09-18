package com.xiyuetsuki.moonclouddrivedemo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ChangePasswordRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.User;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.UserMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试 —— 纯 Mockito，不依赖 Spring 容器或数据库
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        stpUtilMock = org.mockito.Mockito.mockStatic(StpUtil.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    // ==================== 注册测试 ====================

    @Test
    void register_shouldInsertUser_whenCodeValid() {
        RegisterRequest req = buildRegisterRequest("user1", "123456", "a@b.com", "888888");
        when(valueOperations.get("user:registry_code:a@b.com")).thenReturn("888888");
        when(passwordEncoder.encode("123456")).thenReturn("encoded_pass");

        userService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("user1");
        assertThat(saved.getPassword()).isEqualTo("encoded_pass");
        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        verify(stringRedisTemplate).delete(eq("user:registry_code:a@b.com"));
    }

    @Test
    void register_shouldThrowException_whenCodeExpired() {
        RegisterRequest req = buildRegisterRequest("user1", "123456", "a@b.com", "888888");
        when(valueOperations.get("user:registry_code:a@b.com")).thenReturn(null);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码已过期");
    }

    @Test
    void register_shouldThrowException_whenCodeMismatch() {
        RegisterRequest req = buildRegisterRequest("user1", "123456", "a@b.com", "888888");
        when(valueOperations.get("user:registry_code:a@b.com")).thenReturn("000000");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误");
    }

    // ==================== 登录测试 ====================

    @Test
    void login_shouldReturnToken_whenCredentialsCorrect() {
        User user = buildUser(1L, "user1", "encoded_pass", "a@b.com");
        LoginRequest req = buildLoginRequest("a@b.com", "raw_pass");

        when(userMapper.selectByEmail("a@b.com")).thenReturn(user);
        when(passwordEncoder.matches("raw_pass", "encoded_pass")).thenReturn(true);
        stpUtilMock.when(() -> StpUtil.login(1L)).then(invocation -> null);
        stpUtilMock.when(StpUtil::getTokenValue).thenReturn("token-abc");

        LoginResponse resp = userService.login(req);

        assertThat(resp.getToken()).isEqualTo("token-abc");
        assertThat(resp.getUsername()).isEqualTo("user1");
    }

    @Test
    void login_shouldThrowException_whenEmailNotRegistered() {
        LoginRequest req = buildLoginRequest("unknown@x.com", "pass");
        when(userMapper.selectByEmail("unknown@x.com")).thenReturn(null);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱未注册");
    }

    @Test
    void login_shouldThrowException_whenPasswordWrong() {
        User user = buildUser(1L, "user1", "encoded_pass", "a@b.com");
        LoginRequest req = buildLoginRequest("a@b.com", "wrong_pass");

        when(userMapper.selectByEmail("a@b.com")).thenReturn(user);
        when(passwordEncoder.matches("wrong_pass", "encoded_pass")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码错误");
    }

    // ==================== 修改密码测试 ====================

    @Test
    void changePassword_shouldUpdate_whenOldPasswordCorrect() {
        User user = buildUser(1L, "user1", "old_encoded", "a@b.com");
        ChangePasswordRequest req = buildChangePwdRequest("old_raw", "new_raw");

        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("old_raw", "old_encoded")).thenReturn(true);
        when(passwordEncoder.matches("new_raw", "old_encoded")).thenReturn(false);
        when(passwordEncoder.encode("new_raw")).thenReturn("new_encoded");

        userService.changePassword(req);

        verify(userMapper).updateById(any(User.class));
        assertThat(user.getPassword()).isEqualTo("new_encoded");
    }

    @Test
    void changePassword_shouldThrowException_whenOldPasswordWrong() {
        User user = buildUser(1L, "user1", "old_encoded", "a@b.com");
        ChangePasswordRequest req = buildChangePwdRequest("wrong_old", "new_raw");

        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrong_old", "old_encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("旧密码错误");
    }

    @Test
    void changePassword_shouldThrowException_whenNewEqualsOld() {
        User user = buildUser(1L, "user1", "old_encoded", "a@b.com");
        ChangePasswordRequest req = buildChangePwdRequest("same_raw", "same_raw");

        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("same_raw", "old_encoded")).thenReturn(true);
        when(passwordEncoder.matches("same_raw", "old_encoded")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能与旧密码相同");
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

    private User buildUser(Long id, String username, String password, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);
        user.setCreateTime(LocalDateTime.now());
        return user;
    }

    private ChangePasswordRequest buildChangePwdRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword(oldPassword);
        req.setNewPassword(newPassword);
        return req;
    }
}