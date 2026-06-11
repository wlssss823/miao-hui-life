# 项目完善 TODO

> 逐项完成，完成一项勾一项。

---

## 🔴 第一部分：确定的问题（需要修）

### 1. Lua 脚本 Redis Stream XADD 沉积死代码

**位置:** `src/main/resources/lua/VerifyRedis.lua:36`

```lua
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
```

Lua 执行了 XADD 写入 Redis Stream，但消费者 `handleVoucherOrder()` 监听的是 RabbitMQ `order_queue`。Redis Stream 里的消息永远没人消费，属于死代码/僵尸数据。

**方案：** 删掉 Lua 中的 XADD 行，消息投递统一走 RabbitMQ。

---

### 2. Controller 上多余的 `@Transactional`

**位置:** `src/main/java/com/xmdp/controller/VoucherOrderController.java:38`

`seckillVoucher()` 方法挂了 `@Transactional`，但方法里只有 Redis Lua 操作 + RabbitMQ 发送，都不是 JDBC 操作，事务根本不生效。反而无意义地占用数据库连接。

**方案：** 删除 Controller 上的 `@Transactional`。

---

### 3. 成员变量 `proxy` 线程不安全

**位置:** `src/main/java/com/xmdp/service/impl/VoucherOrderServiceImpl.java:59,132`

```java
private IVoucherOrderService proxy;  // 类字段

// 在 handleVoucherOrder() 中赋值
proxy = (IVoucherOrderService) AopContext.currentProxy();
```

多线程消费 MQ 时，多个线程会覆盖同一个字段。

**方案：** 改为方法内局部变量。

---

### 4. `CACHE_SHOP_TYPE_KEY` 和 `CACHE_SHOP_KEY` 值重复

**位置:** `src/main/java/com/xmdp/utils/RedisConstants.java`

```java
CACHE_SHOP_KEY = "cache:shop:"
CACHE_SHOP_TYPE_KEY = "cache:shop:"  // 一样！
```

店铺分类和店铺的缓存 key 冲突。

**方案：** `CACHE_SHOP_TYPE_KEY` 改为 `"cache:shop-type:"`。

---

### 5. MySQL 驱动版本不对

**位置:** `src/main/resources/application.yaml`

```yaml
driver-class-name: com.mysql.jdbc.Driver  # MySQL 5.x 驱动
```

MySQL 8.0 应使用 `com.mysql.cj.jdbc.Driver`。同时建议升级 Maven 依赖的 MySQL Connector 版本。

---

### 6. 消费异常无限重试

**位置:** `src/main/java/com/xmdp/service/impl/VoucherOrderServiceImpl.java:141`

```java
channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
```

`requeue=true` 导致异常消息无限重新入队，形成死循环。

**方案：** 增加重试次数判断，超过阈值进入死信队列或记日志后丢弃（`requeue=false`）。

---

## 🟠 第二部分：架构层面加强

### 7. 缺少限流

秒杀接口没有任何限流机制，高并发下 Redis 和 RabbitMQ 可能被打爆。

**方案：**
- 网关层 Nginx 限流（`limit_req`）
- 应用层 Redisson `RRateLimiter` 或 Sentinel 整合

---

### 8. 缺少本地缓存兜底（三级缓存）

目前店铺查询：`Redis → DB`，热门店铺数据反复查 Redis。

**方案：** 引入 Caffeine 作为一级本地缓存，形成 `Caffeine → Redis → DB` 三级缓存。进一步减少 Redis 压力，降低响应延迟。

---

### 9. 布隆过滤器防穿透

目前缓存穿透只靠缓存空值（`queryWithPassThrough`），可以用布隆过滤器预先拦截肯定不存在的 key，减少 Redis 空值缓存写入。

---

### 10. `queryWithMutex` 递归自旋可能栈溢出

**位置:** `src/main/java/com/xmdp/utils/CacheClient.java:206`

```java
Thread.sleep(50);
return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);  // 递归
```

高并发、长阻塞场景下递归深度可能爆栈。

**方案：** 改为 `while` 循环。

---

### 11. 线程池没有优雅关闭

**位置:** `src/main/java/com/xmdp/utils/CacheClient.java:28`

```java
private static final ExecutorService CACHE_EXEC_POOL = Executors.newFixedThreadPool(10);
```

- 无界队列，任务积压可能 OOM
- 没有 shutdown 机制，应用关闭时丢任务

**方案：** 改用 `ThreadPoolExecutor` 配合有界队列，注册 Spring `@PreDestroy` 钩子优雅关闭。

---

### 12. 订单状态永远停在"未支付"

`tb_voucher_order` 定义了 6 种状态，但目前创建完订单就结束了，没有后续的状态流转。

**方案（可选）：** 根据面试需要补：
- 支付模拟接口（修改状态）
- 超时未支付自动取消（延迟消息 / 定时任务）
- 核销流程

---

## 🟡 第三部分：代码质量

### 13. 全局异常处理粒度太粗

**位置:** `src/main/java/com/xmdp/config/WebExceptionAdvice.java`

```java
@ExceptionHandler(RuntimeException.class)
public Result handleRuntimeException(RuntimeException e) {
    return Result.fail("服务器异常");
}
```

所有 RuntimeException 返回同样的提示，前端无法区分"库存不足"和"重复下单"。

**方案：** 定义业务异常类或细化异常处理，区分可预知的业务错误和系统错误。

---

### 14. RabbitMQ 消费并发度 = 1

```java
@RabbitListener(queues = "order_queue", concurrency = "1")
```

单线程消费限制了秒杀订单处理吞吐量。

**方案：** 根据业务量适当提升并发度（注意分布式锁争抢问题）。

---

### 15. 无单元测试

**位置:** `src/test/java/com/xmdp/`

核心业务（秒杀幂等、Lua 脚本、分布式锁、缓存策略）没有任何测试覆盖。

**方案：** 至少覆盖：
- Redisson 锁 + 事务幂等
- Lua 脚本原子性
- CacheClient 三种策略
- RedisIdGenerator 边界情况

---

## 📝 完成记录

| # | 内容 | 完成日期 | 备注 |
|---|------|---------|------|
| 2 | 删除 Controller 多余 @Transactional | 2026-05-25 | Redis/MQ 非 JDBC 操作，事务无效 |
| 3 | proxy 成员变量改为局部变量 | 2026-05-25 | 多线程并发时类字段被覆盖 |
| 4 | CACHE_SHOP_TYPE_KEY 与 CACHE_SHOP_KEY 冲突 | 2026-05-25 | shop-type 改为独立前缀 |
| 5 | MySQL 驱动升级 5.1.47 → 8.0.30 | 2026-05-25 | 驱动类 + pom 版本 |
| 6 | 消费异常无限重试修复 | 2026-05-25 | x-retry-count 头控制，超 3 次丢弃 |
| 7 | 秒杀接口限流 | 2026-05-25 | Redisson RRateLimiter 令牌桶，200/s |
| 8 | 本地缓存兜底（三级缓存） | 2026-05-25 | Caffeine → Redis → DB，更新时失效 |
| 9 | 布隆过滤器防穿透 | 2026-05-25 | Redisson RBloomFilter，预热已有 ID |
| 10 | queryWithMutex 递归改 while 循环 | 2026-05-25 | 防止栈溢出 |
| 11 | 线程池优雅关闭 | 2026-05-25 | ThreadPoolExecutor + @PreDestroy |
| 12 | 订单支付状态流转 | 2026-05-25 | payOrder 接口，未支付→已支付 |
| 13 | 异常处理粒度细化 | 2026-05-25 | BusinessException 区分业务异常 |
| 14 | MQ 消费并发度 1→4 | 2026-05-25 | 提升订单处理吞吐 |
| 15 | 单元测试 | 2026-05-25 | Lua 脚本原子性测试 + BusinessException |
| 16 | 核销流程（商家端） | 2026-05-25 | verifyOrder 接口，已支付→已核销 |
| 17 | 退款/售后流程 | 2026-05-25 | refundOrder 接口，已支付→已退款 |
| 18 | 关注店铺 + 上新提醒 | 2026-05-25 | followShop + ZSet 推送上新通知 |
| 19 | 超时未支付自动取消 | 2026-05-25 | RabbitMQ 死信队列 30 分钟 TTL |
| 20 | 购物车 | 2026-05-25 | Redis Hash 存储，增删改查 |
| 21 | 评价系统 | 2026-05-25 | tb_review 表 + ZSet 最新评价 |
| 22 | 商家数据看板 | 2026-05-25 | HyperLogLog UV + String PV + ZSet 销量排行 |
| 23 | Spring Boot 2.7 → 3.3.5 升级 | 2026-05-25 | Java 17, Jakarta EE, MyBatis-Plus 3.5.7 |
| 24 | 消息中心（SSE 实时推送） | 2026-05-25 | SseEmitter + 多类型通知 |
| 25 | Knife4j API 文档 | 2026-05-25 | OpenAPI 3 @Tag/@Operation 注解 |
| 26 | PV/UV 拦截器 | 2026-05-25 | HandlerInterceptor 统一采集 |
| 27 | IP 防刷 | 2026-05-25 | Lua 滑动窗口限流，10次/分钟/IP |
| 28 | 链路追踪 traceId | 2026-05-25 | OncePerRequestFilter + MDC |
| 29 | 评价晒图增强 | 2026-05-25 | 图片上传/删除，用户信息批量查询 |
| 30 | 商家管理后台 API | 2026-05-25 | Dashboard 数据看板 + 订单管理列表 |
| 31 | 秒杀商品详情页静态化 | 2026-05-25 | Redis 10s 缓存，含时间/购买状态 |

---

## 🔵 第四部分：业务功能扩展

### 16. 核销流程（商家端）

目前订单支付后没有核销，商家无法验证用户到店消费。

**方案：**
- 新增 `POST /voucher-order/verify/{orderId}` 核销接口
- 校验订单状态为"已支付" → 改为"已核销"，记录核销时间
- 仅订单所属优惠券的商家可核销

---

### 17. 退款/售后流程

补全订单状态流转：已支付 → 退款中 → 已退款。

**方案：**
- 新增 `POST /voucher-order/refund/{orderId}` 退款申请接口
- 校验：订单已支付、未核销
- 状态改为"退款中"，设置退款时间
- 简单模式：直接自动退款成功（状态→已退款）
- 可扩展：RabbitMQ 延迟消息处理超时未处理的退款

---

### 18. 关注店铺 + 上新提醒

目前只能关注人，不能关注店铺。店铺发布新优惠券时通知粉丝。

**方案：**
- 新增 `tb_follow_shop` 表或 Redis Set 存储用户关注的店铺
- 新增 `POST /shop/follow/{shopId}` 关注/取关接口
- 店铺发布优惠券时，遍历关注者 ZSet 推送上新通知（复用写扩散模式）
- 新增 `GET /shop/follow/vouchers` 查看关注店铺的优惠券列表

---

## 🟢 第五部分：更多业务扩展

### 19. 超时未支付自动取消

秒杀下单后 30 分钟未支付自动取消，释放库存和 Redis Set。

**方案：**
- RabbitMQ 死信队列：下单时发一条 TTL=30min 的消息
- 过期后路由到取消订单队列
- 检查订单状态，未支付 → 取消 + 恢复 Redis 库存

---

### 20. 购物车

用户将商品加入购物车，批量结算。

**方案：**
- Redis Hash 存储：`cart:{userId}` → `{shopId: amount}`
- 增删改查都在 Redis 完成，不入 DB
- 结算时选中商品生成订单

---

### 21. 评价系统

核销后可写评价（图文 + 评分）。

**方案：**
- `tb_review` 表（订单 ID、评分、内容、图片）
- Redis ZSet 存储最新评价列表、店铺评分排行

---

### 22. 商家数据看板

商家查看店铺的今日浏览 UV/PV、销量排行。

**方案：**
- Redis HyperLogLog 计算 UV（去重）
- String 累加 PV
- ZSet 做销量排行

---

## 📝 完成记录
