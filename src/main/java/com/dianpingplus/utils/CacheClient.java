package com.dianpingplus.utils;

import cn.hutool.json.JSONUtil;
import com.dianpingplus.cache.CacheStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存客户端 — Strategy Pattern Context
 * <p>
 * 根据策略名称路由到具体的 CacheStrategy 实现，
 * 调用方只需指定策略名即可切换缓存行为。
 * </p>
 *
 * <pre>
 *    cacheClient.query("logicalExpire", key, id, type, dbFallback, ttl, unit);
 *    cacheClient.query("passThrough",   key, id, type, dbFallback, ttl, unit);
 *    cacheClient.query("mutex",         key, id, type, dbFallback, ttl, unit);
 * </pre>
 */
@Component
public class CacheClient {

    private final Map<String, CacheStrategy> strategyMap = new HashMap<>();

    public CacheClient(List<CacheStrategy> strategies) {
        for (CacheStrategy s : strategies) {
            strategyMap.put(s.name(), s);
        }
    }

    // ========== Strategy 路由 ==========

    /**
     * 按策略名执行缓存查询
     *
     * @param strategy  策略名（passThrough / logicalExpire / mutex）
     * @param keyPrefix Redis key 前缀
     * @param id        业务 ID
     * @param type      返回类型
     * @param dbFallback DB 回查函数
     * @param time      TTL 数值
     * @param unit      TTL 单位
     * @param <T>       返回类型泛型
     * @param <ID>      ID 类型泛型
     * @return 缓存数据
     */
    public <T, ID> T query(String strategy, String keyPrefix, ID id, Class<T> type,
                           Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        CacheStrategy s = strategyMap.get(strategy);
        if (s == null) {
            throw new IllegalArgumentException("Unknown cache strategy: " + strategy);
        }
        return s.query(keyPrefix, id, type, dbFallback, time, unit);
    }

    // ========== 通用工具方法 ==========

    /**
     * 直接写入缓存（带 TTL）
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 写入逻辑过期缓存
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ========== 注入 ==========

    @Resource
    private StringRedisTemplate stringRedisTemplate;
}
