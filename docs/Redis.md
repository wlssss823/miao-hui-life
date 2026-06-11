# Redis 使用说明

Redis 使用 database 1，通过 `StringRedisTemplate`（Lettuce 客户端）和 `RedissonClient` 两种方式访问。

## 数据持久化方案

### StringRedisTemplate（常规缓存/业务数据）

| 用途 | Key 前缀 | 数据结构 | 所在类 |
|------|----------|----------|--------|
| 登录验证码 | `login:code:` | String | UserServiceImpl |
| 登录会话 | `login:token:` | Hash | RefreshTokenInterceptor |
| 店铺缓存 | `cache:shop:` | String(JSON) | ShopServiceImpl / CacheClient |
| 店铺类型缓存 | `cache:shop-type:` | String(JSON) | ShopTypeServiceImpl |
| 店铺 GEO | `shop:geo:` | Geo | ShopServiceImpl |
| 秒杀库存 | `seckill:stock:` | String | VoucherOrderServiceImpl |
| 秒杀订单集合 | `seckill:order:` | Set | VoucherOrderServiceImpl |
| 秒杀商品详情 | `seckill:detail:` | String(JSON) | VoucherServiceImpl |
| IP 限流 | `rate:ip:` | String | IpRateLimiter |
| 博客点赞 | `blog:liked:` | Set | BlogServiceImpl |
| 用户签到 | `sign:` | BitMap | UserServiceImpl |
| 推送时间线 | `feed:` | ZSet | BlogServiceImpl |
| 店铺销量排行 | `shop:sales:` | ZSet | PvUvInterceptor |
| 店铺 PV | `shop:pv:` | String | PvUvInterceptor |
| 店铺 UV | `shop:uv:` | HyperLogLog | PvUvInterceptor |
| 评价列表 | `review:shop:` | ZSet | ReviewServiceImpl |
| 秒杀全局限流 | `rate:limiter:seckill` | RRateLimiter | VoucherOrderServiceImpl |
| 购物车 | `cart:` | Hash | CartController |

### CacheClient 工具类

三种缓存模式：
- **PassThrough** — 直接缓存 + 缓存空值防穿透
- **LogicalExpire** — 逻辑过期实现缓存重建（互斥锁）
- **Mutex** — 互斥锁防缓存击穿

### RedissonClient（分布式锁/中间件）

| 用途 | Key | 类型 |
|------|-----|------|
| 秒杀一人一单 | `lock:order:{userId}` | RLock |
| 布隆过滤器 | `bloom:shop` | RBloomFilter |
| 令牌桶限流 | `rate:limiter:seckill` | RRateLimiter |

## Lua 原子脚本

### VerifyRedis.lua（秒杀核心）

```lua
-- 入参：voucherId, userId, orderId
-- 返回：0=正常 1=库存不足 2=重复下单

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if(tonumber(redis.call('get', stockKey)) <= 0) then return 1 end
if(tonumber(redis.call('sismember', orderKey, userId)) == 1) then return 2 end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
```

三条 Redis 命令通过 Lua 脚本封装为一次网络 IO + 原子执行。

### IpRateLimiter.lua（IP 限流）

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local expire = tonumber(ARGV[2])
local current = redis.call('incr', key)
if current == 1 then
    redis.call('expire', key, expire)
end
if current > limit then
    return 0
end
return 1
```

滑动窗口计数器，每个 IP 独立 Key，自动过期。

## 键设计规范

所有 Key 采用 `业务域:功能:标识` 的分段格式，如 `login:code:136xxx`、`seckill:stock:101`。
