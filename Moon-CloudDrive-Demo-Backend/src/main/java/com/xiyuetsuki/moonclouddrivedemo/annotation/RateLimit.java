package com.xiyuetsuki.moonclouddrivedemo.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流维度 */
    RateLimitDimension dimension() default RateLimitDimension.USER;

    /** 时间窗口内允许的最大请求数 */
    int maxRequests() default 3;

    /** 时间窗口，单位秒 */
    int windowSeconds() default 60;

    /** 限流 key 前缀，默认使用类名#方法名 */
    String key() default "";

    /** 限流提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}