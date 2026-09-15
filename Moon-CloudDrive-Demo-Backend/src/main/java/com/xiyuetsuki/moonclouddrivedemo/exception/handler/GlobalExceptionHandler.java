package com.xiyuetsuki.moonclouddrivedemo.exception.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 *
 * Spring 会优先匹配最具体的异常类型，找不到才向上匹配父类，
 * 因此不要在本类中直接 catch Exception 后手动 instanceof 分发，
 * 利用 Spring 的类型匹配机制即可
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 — 用户操作/输入引起的可预期错误（文件不存在、权限不足等）
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Response.bad(400, e.getMessage());
    }

    /**
     * 限流异常 — 请求频率超限
     */
    @ExceptionHandler(RateLimitException.class)
    public Response<Void> handleRateLimitException(RateLimitException e) {
        log.warn("限流触发: {}", e.getMessage());
        return Response.bad(429, e.getMessage());
    }

    /**
     * 未登录/登录失效
     */
    @ExceptionHandler(NotLoginException.class)
    public Response<Void> handleNotLoginException(NotLoginException e) {
        log.warn("未登录访问: {}", e.getMessage());
        return Response.bad(401, "未登录或登录已过期");
    }

    /**
     * 无权限（缺少角色或权限）
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public Response<Void> handleNotPermissionException(RuntimeException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Response.bad(403, "无权限访问");
    }

    /**
     * 未预期的运行时异常 — 服务端内部错误（OSS故障、IO异常等），不向客户端暴露细节
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e) {
        log.error("服务端异常: {}", e.getMessage(), e);
        return Response.bad(500, "服务器内部错误");
    }

    /**
     * 兜底 — 其他所有未捕获异常
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        return Response.bad(500, "服务器内部错误");
    }
}