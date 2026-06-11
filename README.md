# 秒惠生活 — 本地生活服务平台

> 2025 年 07 月 - 2025 年 10 月

**技术栈：** SpringBoot + MySQL + Redis + Lua + RabbitMQ + Caffeine + Redisson + MybatisPlus 等

**项目简介：** 秒惠生活为用户提供了商家信息查询、秒杀优惠券、智能客服等功能，同时帮助商家推广优惠信息。

---

## 核心工作

### 1. 秒杀防超卖和一人一单

使用 Redis 存储库存和订单信息，配合 Lua 脚本判断用户下单资格，一次网络 IO 完成库存检查、幂等判断、扣减库存、记录用户，保证库存不超卖和一人一单。

```
Lua 脚本原子操作（VerifyRedis.lua）
├─ 检查库存 seckill:stock:{id} > 0
├─ 检查重复下单 seckill:order:{id} 不含 userId
├─ INCRBY stock -1（扣减库存）
└─ SADD order（记录订单用户）
```

### 2. 秒杀流程优化

修改同步流程，用户下单后使用消息队列异步处理库存扣减和订单生成，提高秒杀场景的并发性能。

```
用户请求 → Lua 原子校验（纯内存过滤）→ RabbitMQ 异步下单 → 消费端落库
                                                    ↑ 秒回前端
```

- RabbitMQ 持久化 + 手动 ACK + Publisher Confirm，保证消息不丢失
- 消费端固定速率落 DB，避免数据库被打爆

### 3. 未支付订单到期自动关闭

使用 Spring Task 定时任务实现未支付订单的到期自动关闭，扫描超时未支付订单，恢复 Redis 库存和用户集合。

### 4. 支付和关单的并发

使用乐观锁解决订单支付和关单的并发问题，通过版本号/CAS 条件更新避免状态覆盖。

### 5. 缓存优化

- **缓存击穿：** 使用逻辑过期方案防止 Redis 热点 Key 的缓存击穿问题，热点数据不设 TTL，通过逻辑过期字段判断是否需要异步刷新
- **缓存穿透：** 使用缓存空值方案解决 Redis Key 的缓存穿透问题，查询不存在的数据时缓存空值并设置短 TTL

### 6. 数据一致性保证

更新数据库后删除缓存，若删除失败通过消息队列补偿重试，TTL 兜底共同保证数据一致性。

```
更新 DB → 删除缓存 → 失败？→ MQ 补偿重试 → TTL 最终兜底
```

### 7. 多级缓存

使用 Caffeine 本地缓存和 Redis 缓存搭建二级缓存架构，提高热点数据访问速度，降低 Redis 压力。

```
请求 → Caffeine（L1 本地）→ Redis 逻辑过期（L2）→ DB
              ↓ 命中              ↓ 命中             ↓ 回填
            直接返回            直接返回          异步写回两级缓存
```

### 8. 滑动窗口限流

使用 Redis + AOP + 注解实现限流，支持全局、IP、用户多维度，防止系统过载、刷券、爬虫。

---

## 系统架构

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
│  前端(Vue)   │────▶│  Nginx(:8080) │────▶│ Spring Boot      │
│              │     │  /api → :8081 │     │  :8081           │
└─────────────┘     └──────────────┘     └────┬─────────────┘
                                              │
                    ┌─────────────────────────┼──────────────────────┐
                    │                         │                      │
                    ▼                         ▼                      ▼
              ┌───────────┐           ┌───────────┐          ┌────────────┐
              │  Redis    │           │  MySQL    │          │  RabbitMQ  │
              │  7.x      │           │  8.0      │          │  3.x       │
              │  db=1     │           │           │          │  /dev      │
              └───────────┘           └───────────┘          └────────────┘
```

---

## 技术栈详情

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.3.5 | 基础框架 |
| Java | 17 | 运行环境 |
| MyBatis-Plus | 3.5.7 | ORM |
| Redis | 7.x | 缓存、分布式锁、GEO、BitMap、HyperLogLog |
| Redisson | 3.37.0 | 分布式锁、布隆过滤器、限流器 |
| RabbitMQ | 3.x | 异步下单、削峰填谷、死信队列 |
| Lua | — | Redis 原子脚本 |
| MySQL | 8.0 | 持久化存储 |
| Caffeine | — | 本地一级缓存 |
| Knife4j | 4.5.0 | API 文档 (SpringDoc OpenAPI 3) |

---

## 功能模块

### 用户模块
- 验证码登录/自动注册（Redis String 存储验证码，2 分钟 TTL）
- 登录 token 双拦截器，自动续期
- BitMap 签到 + 连续签到天数统计

### 店铺模块
- 二级缓存查询（Caffeine → Redis 逻辑过期 → DB）
- GEO 附近店铺查询（按距离排序 + 分页）
- 关注店铺、PV/UV 统计、商家数据看板

### 秒杀模块
- Lua 原子校验 + MQ 异步下单 + 三层幂等
- IP 防刷（Lua 滑动窗口限流）
- 令牌桶全局限流（Redisson RRateLimiter）
- 乐观锁防超卖

### 订单模块
- 订单全生命周期（未支付 → 已支付 → 已核销 → 已退款/已取消）
- 模拟支付、商家核销、申请退款
- 超时自动取消（定时任务 / 死信队列）

### 内容模块
- 博客点赞（ZSet 排序）、关注推流（写扩散）、滚动分页
- 博客评论、评价系统（图文评价 + 评分）

### 消息中心
- SSE 实时推送、通知列表、未读管理

---

## 秒杀请求完整链路

```
① Nginx (/api/voucher-order/seckill/{id})
      │
② Controller → VoucherOrderController
      │
③ IP 防刷（IpRateLimiter Lua：每个 IP 每分钟最多 10 次）
      │
④ 限流（Redisson RRateLimiter 令牌桶 200/s）
      │
⑤ 验证秒杀时间窗口（begin_time / end_time）
      │
⑥ Lua 脚本原子操作（VerifyRedis.lua）
  ├─ 检查库存 seckill:stock:{id} > 0
  ├─ 检查重复下单 seckill:order:{id} 不含 userId
  ├─ INCRBY stock -1（扣减库存）
  └─ SADD order（记录订单用户）
      │
⑦ return 1→库存不足 / 2→重复下单 / 0→成功
      │
⑧ RabbitMQ 发送（Publisher Confirm）
  ├─ Exchange: amq.direct
  ├─ RoutingKey: dianping.order
  └─ Queue: order_queue（持久化）
      │
⑨ @RabbitListener 消费（手动 ACK，并发 4）
      │
⑩ Redisson 锁 lock:order:{userId}（一人一单）
      │
⑪ @Transactional createVoucherOrder()
  ├─ DB 查重（三层幂等兜底）
  ├─ 乐观锁扣库存（WHERE stock > 0）
  ├─ INSERT 订单
  └─ 事务提交后 → 延迟取消消息 + 下单成功通知
      │
⑫ 手动 ACK / NACK 重试（最多 3 次，超限进死信队列）
```

---

## 本地启动

```bash
# 1. 启动基础设施（MySQL + Redis + RabbitMQ + Nginx）
docker compose up -d

# 2. 初始化 Redis 店铺 GEO 数据
bash init-redis-geo.bat

# 3. 启动 Spring Boot
mvn spring-boot:run

# 4. 访问前端 http://localhost:8080
#    API 文档 http://localhost:8081/doc.html
```

### 测试账号

| 手机号 | 说明 |
|--------|------|
| 13686869696 | 用户"小鱼同学" |
| 13838411438 | 其他测试用户 |

登录方式：手机号 → 发送验证码 → 验证码自动填入（开发环境直接返回）

---

## 项目文档

| 文档 | 内容 |
|------|------|
| [MQ 设计](docs/MQ.md) | RabbitMQ 队列、交换机、死信队列配置 |
| [Redis 设计](docs/Redis.md) | Redis 数据结构使用场景 |
| [秒杀逻辑](docs/秒杀逻辑.md) | 秒杀完整流程设计 |
| [亮点总结](docs/亮点.md) | 面试可聊的技术亮点 |
| [完善记录](docs/TODO.md) | 问题修复与技术扩展记录 |
