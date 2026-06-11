package com.dianpingplus.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dianpingplus.utils.RedisConstants.CACHE_NULL_TTL;

/**
 * 缓存穿透策略：未命中时缓存空值
 */
@Component
public class PassThroughCacheStrategy implements CacheStrategy {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String name() {
        return "passThrough";
    }

    @Override
    public <T, ID> T query(String keyPrefix, ID id, Class<T> type,
                           Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        T r = dbFallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
        return r;
    }
}
