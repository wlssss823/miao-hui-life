package com.dianpingplus.cache;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dianpingplus.utils.RedisData;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dianpingplus.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * 逻辑过期策略：过期后异步重建缓存，防止缓存击穿
 */
@Slf4j
@Component
public class LogicalExpireCacheStrategy implements CacheStrategy {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            10, 10,
            0L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdown() {
        log.info("LogicalExpireCacheStrategy 线程池关闭中...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String name() {
        return "logicalExpire";
    }

    @Override
    public <T, ID> T query(String keyPrefix, ID id, Class<T> type,
                           Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            T res = dbFallback.apply(id);
            if (res == null) {
                setWithLogicalExpire(key, "", time, unit);
                return null;
            }
            setWithLogicalExpire(key, res, time, unit);
            return res;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }

        // 逻辑过期 → 互斥锁异步重建
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            executor.submit(() -> {
                try {
                    T newRes = dbFallback.apply(id);
                    setWithLogicalExpire(key, newRes, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        redisData = JSONUtil.toBean(json, RedisData.class);
        r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        return r;
    }

    private void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
