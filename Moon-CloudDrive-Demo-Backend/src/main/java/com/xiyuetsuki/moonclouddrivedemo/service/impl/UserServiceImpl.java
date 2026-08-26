package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.LoginResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.User;
import com.xiyuetsuki.moonclouddrivedemo.mapper.UserMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String REDIS_KEY_PREFIX = "user:registry_code:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final int CODE_LENGTH = 6;

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${spring.mail.username}")
    private String mailUsername;

    /**
     * 发送邮箱验证码
     * @param email
     */
    @Override
    public void sendVerifyCode(String email) {
        String code = generateCode();
        String redisKey = REDIS_KEY_PREFIX + email;

        stringRedisTemplate.opsForValue().set(redisKey, code, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(email);
            message.setSubject("Moon-CloudDrive 注册验证码");
            message.setText("您的验证码是：" + code + "，有效期" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。");

            mailSender.send(message);
            log.info("验证码已发送至邮箱: {}", email);
        } catch (Exception e) {
            stringRedisTemplate.delete(redisKey);
            log.error("邮件发送失败，已清除验证码: {}", email, e);
            throw new RuntimeException("邮件发送失败，请检查邮箱地址或稍后重试", e);
        }
    }

    /**
     * 用户注册
     * @param request
     */
    @Override
    public void register(RegisterRequest request) {
        String email = request.getEmail();
        String redisKey = REDIS_KEY_PREFIX + email;

        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (storedCode == null) {
            throw new RuntimeException("验证码已过期或未发送");
        }
        if (!storedCode.equals(request.getCode())) {
            throw new RuntimeException("验证码错误");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(email);
        user.setCreateTime(LocalDateTime.now());

        userMapper.insert(user);

        stringRedisTemplate.delete(redisKey);
        log.info("用户注册成功: {}", email);
    }

    /**
     * 用户登陆
     * @param request
     * @return
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectByEmail(request.getEmail());
        if (user == null) {
            throw new RuntimeException("邮箱未注册");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        StpUtil.login(user.getId());

        String token = StpUtil.getTokenValue();
        log.info("用户登录成功: {}", request.getEmail());

        return new LoginResponse(token, user.getUsername(), user.getEmail());
    }

    /**
     * 生成验证码
     * @return
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}