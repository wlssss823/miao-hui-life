# RabbitMQ 使用说明

## 使用场景

MQ 在以下场景使用：

| 场景 | 队列 | 说明 |
|------|------|------|
| 秒杀异步下单 | `order_queue` | Lua 校验通过后异步落库，削峰填谷 |
| 超时自动取消 | `order_delay_queue` | 死信队列，30 分钟 TTL 未支付自动取消 |

## 配置类：RabbitConfig

### 秒杀订单队列

- **Exchange**: `amq.direct`（RabbitMQ 自带 Direct Exchange）
- **Queue**: `order_queue`（持久化）
- **Routing Key**: `dianping.order`
- **消费并发**: 4 线程
- **确认模式**: 手动 ACK

### 超时取消死信队列

- **延迟队列**: `order_delay_queue`
  - `x-message-ttl`: 1800000ms（30 分钟）
  - `x-dead-letter-exchange`: `amq.direct`
  - `x-dead-letter-routing-key`: `dianping.order.cancel`
- **取消队列**: `order_cancel_queue`（绑定 routing key `dianping.order.cancel`）

## 发送端：VoucherOrderServiceImpl#seckillVoucher

1. Lua 脚本原子校验（Redis 库存 + 重复下单检查）
2. 校验通过后，将 `VoucherOrder` 序列化为 JSON 发送到 `order_queue`
3. 设置 `ConfirmCallback`（确认到达 Exchange）和 `ReturnsCallback`（确认路由到 Queue）

## 消费端：VoucherOrderServiceImpl#handleVoucherOrder

- `@RabbitListener(queues = "order_queue", concurrency = "4")` 异步消费
- 手动 ACK（`basicAck`）
- 消费时做 Redisson 一人一单锁判断
- 最终调用 `createVoucherOrder()` 事务落库
- 异常重试：`x-retry-count` 消息头控制，最多 3 次，超限丢弃（不进死信队列）

## 超时取消消费端：VoucherOrderServiceImpl#handleOrderCancel

- 监听 `order_cancel_queue`
- 查询订单状态，仅取消未支付订单
- 恢复 Redis 库存（`INCR seckill:stock:{id}`）
- 移除 Redis 下单用户记录（`SREM seckill:order:{id}`）
- 发送订单取消通知

## 设计意图

### 异步削峰

秒杀请求先通过 Lua 脚本在 Redis 层做原子校验（库存+幂等），通过后立即返回成功响应，MQ 异步消费落库，将数据库写入压力削峰。

### 可靠投递

- Publisher Confirm：确认消息到达 Exchange
- Returns Callback：确认消息路由到 Queue
- 消息持久化（DeliveryMode.PERSISTENT）

### 死信队列自动取消

下单成功后发送一条 30 分钟 TTL 的延迟消息，到期未支付自动取消，确保库存及时释放。
