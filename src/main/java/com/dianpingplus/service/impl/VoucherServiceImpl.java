package com.dianpingplus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.FollowShop;
import com.dianpingplus.entity.Voucher;
import com.dianpingplus.mapper.FollowShopMapper;
import com.dianpingplus.mapper.VoucherMapper;
import com.dianpingplus.entity.SeckillVoucher;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.service.ISeckillVoucherService;
import com.dianpingplus.service.IVoucherService;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dianpingplus.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowShopMapper followShopMapper;

    @Resource
    private INoticeService noticeService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());

        //保存到Redis中
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());

        seckillVoucherService.save(seckillVoucher);

        // 推送上新通知给关注该店铺的用户
        List<FollowShop> followers = followShopMapper.selectList(
                new QueryWrapper<FollowShop>().eq("shop_id", voucher.getShopId()));
        long now = System.currentTimeMillis();
        for (FollowShop follower : followers) {
            String feedKey = "feed:shop.voucher:" + follower.getUserId();
            stringRedisTemplate.opsForZSet().add(feedKey, voucher.getId().toString(), now);
            noticeService.addNotice(follower.getUserId(), 3, "店铺上新",
                    "您关注的店铺上新了优惠券：" + voucher.getTitle(), voucher.getId());
        }
    }

    @Override
    public Result queryFollowShopVouchers() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String feedKey = "feed:shop.voucher:" + userId;

        // 查询关注店铺的上新通知 ZSet，按时间倒序取前 20
        Set<String> voucherIds = stringRedisTemplate.opsForZSet()
                .reverseRange(feedKey, 0, 19);
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 根据 ID 查询优惠券详情
        List<Long> ids = voucherIds.stream().map(Long::valueOf).collect(Collectors.toList());
        List<Voucher> vouchers = listByIds(ids);
        return Result.ok(vouchers);
    }

    @Override
    public Result querySeckillDetail(Long voucherId) {
        // 1. 尝试从缓存获取
        String cacheKey = "seckill:detail:" + voucherId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Result.ok(cn.hutool.json.JSONUtil.parseObj(cached));
        }

        // 2. 查询基本信息
        Voucher voucher = getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("非秒杀商品");
        }

        // 3. 查询实时库存
        String stockStr = stringRedisTemplate.opsForValue().get(SECKILL_STOCK_KEY + voucherId);
        int realtimeStock = stockStr == null ? (seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock()) : Integer.parseInt(stockStr);

        // 4. 当前时间状态
        LocalDateTime now = LocalDateTime.now();
        int timeStatus;
        if (now.isBefore(seckillVoucher.getBeginTime())) {
            timeStatus = 0; // 未开始
        } else if (now.isAfter(seckillVoucher.getEndTime())) {
            timeStatus = 2; // 已结束
        } else {
            timeStatus = 1; // 进行中
        }

        // 5. 当前用户购买状态
        boolean hasBought = false;
        try {
            Long userId = ThreadLocalUserUtils.getUser().getId();
            Boolean member = stringRedisTemplate.opsForSet()
                    .isMember("seckill:order:" + voucherId, userId.toString());
            hasBought = Boolean.TRUE.equals(member);
        } catch (Exception ignored) {}

        // 6. 组装详情
        Map<String, Object> detail = new HashMap<>();
        detail.put("voucherId", voucher.getId());
        detail.put("shopId", voucher.getShopId());
        detail.put("title", voucher.getTitle());
        detail.put("subTitle", voucher.getSubTitle());
        detail.put("rules", voucher.getRules());
        detail.put("payValue", voucher.getPayValue());
        detail.put("actualValue", voucher.getActualValue());
        detail.put("type", voucher.getType());
        detail.put("stock", realtimeStock);
        detail.put("beginTime", seckillVoucher.getBeginTime());
        detail.put("endTime", seckillVoucher.getEndTime());
        detail.put("timeStatus", timeStatus);
        detail.put("hasBought", hasBought);

        // 7. 写入缓存（10 秒过期，短 TTL 保证数据新鲜）
        stringRedisTemplate.opsForValue().set(cacheKey,
                cn.hutool.json.JSONUtil.toJsonStr(detail), 10, TimeUnit.SECONDS);

        return Result.ok(detail);
    }
}
