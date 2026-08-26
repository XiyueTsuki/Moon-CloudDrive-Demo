package com.xiyuetsuki.moonclouddrivedemo.config;

import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e) {
        log.error("业务异常: {}", e.getMessage());
        return Response.bad(500, e.getMessage());
    }
}