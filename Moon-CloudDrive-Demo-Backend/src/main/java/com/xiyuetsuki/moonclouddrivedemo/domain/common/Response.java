package com.xiyuetsuki.moonclouddrivedemo.domain.common;

import lombok.Data;

@Data
public class Response<T> {
    private int code;
    private T data;
    private String msg;

    public Response() {
    }

    public Response(int code, String msg) {
        this.code = code;
        this.data = null;
        this.msg = msg;
    }

    public Response(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public static <T> Response<T> ok(String msg) {
        return new Response<>(200, msg);
    }

    public static <T> Response<T> ok(T data, String msg) {
        return new Response<>(200, data, msg);
    }

    public static <T> Response<T> bad(int code, T data, String msg) {
        return new Response<>(code, data, msg);
    }

    public static <T> Response<T> bad(int code, String msg) {
        return new Response<>(code, msg);
    }
}