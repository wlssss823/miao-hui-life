package com.dianpingplus.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;

/**
 * IP 级速率限制器，基于 Redis 滑动窗口（固定窗口 + 自动过期）
 */
@Component
public class IpRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT;

    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setScriptText(
                "local key = KEYS[1]\n" +
                "local limit = tonumber(ARGV[1])\n" +
                "local expire = tonumber(ARGV[2])\n" +
                "local current = redis.call('incr', key)\n" +
                "if current == 1 then\n" +
                "    redis.call('expire', key, expire)\n" +
                "end\n" +
                "if current > limit then\n" +
                "    return 0\n" +
                "end\n" +
                "return 1\n"
        );
        SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @param ip            客户端 IP
     * @param limit         窗口内最大请求数
     * @param windowSeconds 时间窗口（秒）
     * @return true=放行 false=限流
     */
    public boolean tryAcquire(String ip, int limit, int windowSeconds) {
        String key = "rate:ip:" + ip;
        Long result = stringRedisTemplate.execute(
                SCRIPT, Collections.singletonList(key),
                String.valueOf(limit), String.valueOf(windowSeconds));
        return result != null && result == 1;
    }
}
