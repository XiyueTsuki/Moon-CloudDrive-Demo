package com.xiyuetsuki.moonclouddrivedemo.validation;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation 独立测试 —— 不依赖 Spring，直接使用 Validator API
 */
class RegisterRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldRejectBlankEmail() {
        RegisterRequest req = buildRequest("user1", "123456", "", "888888");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("邮箱不能为空");
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        RegisterRequest req = buildRequest("user1", "123456", "not-an-email", "888888");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("邮箱格式不正确");
    }

    @Test
    void shouldRejectShortPassword() {
        RegisterRequest req = buildRequest("user1", "123", "a@b.com", "888888");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("密码长度不能少于6位");
    }

    @Test
    void shouldPassWhenAllFieldsValid() {
        RegisterRequest req = buildRequest("user1", "123456", "a@b.com", "888888");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    private RegisterRequest buildRequest(String username, String password, String email, String code) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setEmail(email);
        req.setCode(code);
        return req;
    }
}