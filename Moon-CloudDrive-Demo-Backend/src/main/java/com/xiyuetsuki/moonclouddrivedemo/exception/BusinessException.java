package com.xiyuetsuki.moonclouddrivedemo.exception;

/**
 * 业务异常
 *
 * 表示用户操作/输入引起的可预期错误（文件不存在、权限不足、验证码错误等），
 * 由全局异常处理器统一转换为 400 响应
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}