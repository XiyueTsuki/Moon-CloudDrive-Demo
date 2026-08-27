package com.xiyuetsuki.moonclouddrivedemo.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimit;
import com.xiyuetsuki.moonclouddrivedemo.annotation.RateLimitDimension;
import com.xiyuetsuki.moonclouddrivedemo.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(pjp, rateLimit);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);

        limiter.trySetRate(RateType.OVERALL,
                rateLimit.maxRequests(),
                rateLimit.windowSeconds(),
                RateIntervalUnit.SECONDS);

        if (!limiter.tryAcquire()) {
            log.warn("限流触发: key={}, dimension={}, maxRequests={}, window={}s",
                    key, rateLimit.dimension(), rateLimit.maxRequests(), rateLimit.windowSeconds());
            throw new RateLimitException(rateLimit.message());
        }

        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        String prefix = rateLimit.key().isEmpty()
                ? pjp.getSignature().getDeclaringTypeName() + "#" + ((MethodSignature) pjp.getSignature()).getMethod().getName()
                : rateLimit.key();

        String dimensionKey = switch (rateLimit.dimension()) {
            case USER -> {
                try {
                    yield "user:" + StpUtil.getLoginIdAsLong();
                } catch (Exception e) {
                    yield "user:anonymous";
                }
            }
            case IP -> {
                try {
                    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        HttpServletRequest request = attrs.getRequest();
                        String ip = request.getHeader("X-Forwarded-For");
                        if (ip == null || ip.isEmpty()) {
                            ip = request.getRemoteAddr();
                        }
                        yield "ip:" + ip;
                    }
                } catch (Exception ignored) {
                }
                yield "ip:unknown";
            }
            case GLOBAL -> "global";
        };

        return "rate_limit:" + prefix + ":" + dimensionKey;
    }
}