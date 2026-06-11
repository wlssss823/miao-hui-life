package com.dianpingplus.cache;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dianpingplus.utils.RedisConstants.CACHE_NULL_TTL;
import static com.dianpingplus.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * 互斥锁策略：缓存未命中时加锁查 DB，其他线程自旋等待
 */
@Component
public class MutexCacheStrategy implements CacheStrategy {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String name() {
        return "mutex";
    }

    @Override
    public <T, ID> T query(String keyPrefix, ID id, Class<T> type,
                           Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;

        while (true) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }

            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                continue;
            }

            try {
                T r = dbFallback.apply(id);
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
                return r;
            } finally {
                unlock(lockKey);
            }
        }
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
