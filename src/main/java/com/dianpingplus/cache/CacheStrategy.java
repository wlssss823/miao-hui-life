package com.dianpingplus.cache;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存策略接口（Strategy Pattern）
 * 三种实现：PassThrough / LogicalExpire / Mutex
 * 由 CacheClient 作为 Context 按名称路由
 */
public interface CacheStrategy {

    String name();

    <T, ID> T query(String keyPrefix, ID id, Class<T> type,
                    Function<ID, T> dbFallback, Long time, TimeUnit unit);
}
