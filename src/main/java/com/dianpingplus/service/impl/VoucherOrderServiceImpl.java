package com.dianpingplus.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.SeckillVoucher;
import com.dianpingplus.entity.VoucherOrder;
import com.dianpingplus.mapper.VoucherOrderMapper;
import com.dianpingplus.service.ISeckillVoucherService;
import com.dianpingplus.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.entity.Voucher;
import com.dianpingplus.service.IVoucherService;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.utils.BusinessException;
import com.dianpingplus.utils.IpRateLimiter;
import com.dianpingplus.utils.RedisIdGenerator;
import com.dianpingplus.utils.RedisConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdGenerator redisIdGenerator;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private INoticeService noticeService;

    @Resource
    private IpRateLimiter ipRateLimiter;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/VerifyRedis.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private RRateLimiter seckillRateLimiter;

    //初始化时设置MQ确认机制和限流器
    @PostConstruct
    private void init(){
        // 全局秒杀限流：每秒最多 200 个请求通过
        seckillRateLimiter = redissonClient.getRateLimiter("rate:limiter:seckill");
        seckillRateLimiter.trySetRate(RateType.OVERALL, 200, 1, RateIntervalUnit.SECONDS);

        rabbitTemplate.setConfirmCallback(((correlationData, ack, cause) -> {
            if(ack){
                log.info("订单发送成功,编号:" +correlationData.getId());
            } else{
                log.error("订单发送失败,原因:" + cause);
            }
        }));

        rabbitTemplate.setReturnsCallback(returnedMessage -> {
            log.error("消息路由失败: 交换机={}, 路由键={}, 原因={}",
                    returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyText());
        });
    }

//老阻塞队列异步下单方法
/*    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //初始化执行线程任务
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //获取阻塞队列任务
                    VoucherOrder voucherOrder = orderTasks.take();
                    //执行
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
*/
    @RabbitListener(queues = "order_queue", concurrency = "4")
    public void handleVoucherOrder(VoucherOrder voucherOrder, Channel channel, Message message) throws IOException {
        try{
            //一人一单，判断是否已有订单 // 悲观锁
            //类内部调用事务方法会导致事务失效createVoucherOrder(voucherId);
            Long userId = voucherOrder.getUserId();
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            boolean isLock = lock.tryLock();

            if(!isLock){
                log.error("不允许重复下单");
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return ;
            }

            try{
                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                proxy.createVoucherOrder(voucherOrder);
            }finally {
                lock.unlock();
            }

            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("创建订单异常", e);
            // 重试次数控制，避免无限 requeue
            Long retryCount = message.getMessageProperties().getHeader("x-retry-count");
            if (retryCount == null) retryCount = 0L;
            if (retryCount >= 3) {
                log.error("订单处理超过最大重试次数，丢弃: orderId={}", voucherOrder.getId());
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
            } else {
                Message retryMsg = MessageBuilder.fromClonedMessage(message)
                        .setHeader("x-retry-count", retryCount + 1)
                        .build();
                rabbitTemplate.convertAndSend("amq.direct", "dianping.order", retryMsg);
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
            }
        }
    }



    /**
     * 创建订单方法
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        long count = query().eq("user_id", userId)
                .eq("voucher_id",voucherId).count();
        if(count > 0){
            log.error("订单已存在");
            return ;
        }

        //扣减库存 !!!防止超买
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!update){
            throw new BusinessException("库存不足");
        }
        //生成订单
        save(voucherOrder);

        // 记录销量（ZSet：店铺销量排行）
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher != null) {
            stringRedisTemplate.opsForZSet()
                    .incrementScore("shop:sales:" + DateUtil.today(), voucher.getShopId().toString(), 1);
        }

        // 事务提交后发送延迟取消消息 & 下单成功通知
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend("amq.direct", "dianping.order.delay",
                        voucherOrder.getId().toString());
                noticeService.addNotice(userId, 2, "下单成功",
                        "您的订单 " + voucherOrder.getId() + " 已创建成功，请尽快完成支付", voucherOrder.getId());
            }
        });
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // IP 防刷：每分钟每个 IP 最多 10 次秒杀请求
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String ip = attrs.getRequest().getRemoteAddr();
            if (!ipRateLimiter.tryAcquire(ip, 10, 60)) {
                return Result.fail("操作太频繁，请稍后再试");
            }
        }

        // 限流：令牌桶，超出直接拒绝
        if (!seckillRateLimiter.tryAcquire()) {
            return Result.fail("秒杀活动太火爆，请稍后再试");
        }

        Long userId = ThreadLocalUserUtils.getUser().getId();
        Long orderId = redisIdGenerator.nextId("order");

        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now()) || seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
        //不能购买
            return Result.fail("不在秒杀卷购买时间区间中");
        }

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();

        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        //用户、优惠券与订单ID存入MQ中（异步下单）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        //MQ添加信息
//        orderTasks.add(voucherOrder);
        sendWithConfirm(voucherOrder);

        return Result.ok(orderId);
    }

    /**
     * 监听超时未支付的订单，自动取消并恢复库存
     */
    @RabbitListener(queues = "order_cancel_queue")
    public void handleOrderCancel(String orderIdStr, Channel channel, Message message) throws IOException {
        try {
            Long orderId = Long.valueOf(orderIdStr);
            VoucherOrder order = getById(orderId);
            if (order == null || order.getStatus() != 1) {
                // 已支付或已取消，无需处理
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }

            // 取消订单
            order.setStatus(4);
            updateById(order);

            // 恢复 Redis 库存
            stringRedisTemplate.opsForValue()
                    .increment(RedisConstants.SECKILL_STOCK_KEY + order.getVoucherId());
            // 移除下单用户记录
            stringRedisTemplate.opsForSet()
                    .remove("seckill:order:" + order.getVoucherId(), order.getUserId().toString());

            noticeService.addNotice(order.getUserId(), 2, "订单已取消",
                    "您的订单 " + orderId + " 因超时未支付已自动取消", orderId);
            log.info("超时订单已取消: orderId={}", orderId);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("取消超时订单异常", e);
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @Override
    public Result payOrder(Long orderId) {
        // 1. 查询订单
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 校验当前用户是否是该订单的拥有者
        Long userId = ThreadLocalUserUtils.getUser().getId();
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }

        // 3. 校验状态（仅未支付可支付）
        if (order.getStatus() != 1) {
            return Result.fail("订单状态异常，无法支付");
        }

        // 4. 模拟支付成功
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);

        noticeService.addNotice(userId, 2, "支付成功",
                "您的订单 " + orderId + " 已支付成功", orderId);
        return Result.ok();
    }

    @Override
    public Result verifyOrder(Long orderId) {
        // 1. 查询订单
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 校验状态（仅已支付可核销）
        if (order.getStatus() != 2) {
            return Result.fail("订单状态异常，无法核销");
        }

        // 3. 核销
        order.setStatus(3);
        order.setUseTime(LocalDateTime.now());
        updateById(order);

        noticeService.addNotice(order.getUserId(), 2, "核销成功",
                "您的订单 " + order.getVoucherId() + " 已核销，欢迎再次光临", orderId);
        return Result.ok();
    }

    @Override
    public Result refundOrder(Long orderId) {
        // 1. 查询订单
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2. 校验当前用户是否是该订单的拥有者
        Long userId = ThreadLocalUserUtils.getUser().getId();
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }

        // 3. 校验状态（仅已支付可退款，已核销不可退）
        if (order.getStatus() != 2) {
            return Result.fail("订单状态异常，无法退款");
        }

        // 4. 退款（模拟自动退款成功）
        order.setStatus(6);
        order.setRefundTime(LocalDateTime.now());
        updateById(order);

        noticeService.addNotice(userId, 2, "退款成功",
                "您的订单 " + orderId + " 已退款成功", orderId);
        return Result.ok();
    }

    /**
     * 查询店铺订单列表（商家后台）
     */
    @Override
    public Result shopOrderList(Long shopId, Integer current, Integer status) {
        // 1. 查询店铺的所有优惠券
        List<Voucher> vouchers = voucherService.lambdaQuery().eq(Voucher::getShopId, shopId).list();
        if (vouchers.isEmpty()) {
            return Result.ok(Collections.emptyList(), 0L);
        }
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());

        // 2. 查询这些优惠券对应的订单（分页）
        var wrapper = lambdaQuery().in(VoucherOrder::getVoucherId, voucherIds);
        if (status != null && status > 0) {
            wrapper = wrapper.eq(VoucherOrder::getStatus, status);
        }
        var page = wrapper.orderByDesc(VoucherOrder::getCreateTime).page(new Page<>(current, 10));

        // 3. 构建返回数据（含 voucher title 映射）
        Map<Long, String> voucherTitleMap = vouchers.stream()
                .collect(Collectors.toMap(Voucher::getId, Voucher::getTitle));
        List<Map<String, Object>> orderList = page.getRecords().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", order.getId());
            map.put("orderId", order.getId());
            map.put("userId", order.getUserId());
            map.put("voucherId", order.getVoucherId());
            map.put("voucherTitle", voucherTitleMap.get(order.getVoucherId()));
            map.put("status", order.getStatus());
            map.put("payType", order.getPayType());
            map.put("createTime", order.getCreateTime());
            map.put("payTime", order.getPayTime());
            map.put("useTime", order.getUseTime());
            return map;
        }).collect(Collectors.toList());

        return Result.ok(orderList, page.getTotal());
    }

    public void sendWithConfirm(VoucherOrder voucherOrder) {
        //CorrelationDate携带优惠券ID
        String ID = voucherOrder.getId().toString() + "-" + voucherOrder.getUserId().toString();
        CorrelationData correlationData = new CorrelationData(ID);
        Message content = MessageBuilder.withBody(JSONUtil.toJsonStr(voucherOrder).getBytes())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        rabbitTemplate.convertAndSend("amq.direct", "dianping.order", content, correlationData);
        log.info("发送消息成功: {}, correlationData:{}", content,correlationData);
    }
}