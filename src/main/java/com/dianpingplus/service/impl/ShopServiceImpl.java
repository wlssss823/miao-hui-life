package com.dianpingplus.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.FollowShop;
import com.dianpingplus.entity.Shop;
import com.dianpingplus.mapper.FollowShopMapper;
import com.dianpingplus.mapper.ShopMapper;
import com.dianpingplus.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.utils.CacheClient;
import com.dianpingplus.utils.SystemConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import com.dianpingplus.service.IVoucherOrderService;
import com.dianpingplus.service.IVoucherService;
import com.dianpingplus.service.IReviewService;
import com.dianpingplus.entity.Voucher;
import com.dianpingplus.entity.VoucherOrder;
import com.dianpingplus.entity.Review;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.dianpingplus.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private FollowShopMapper followShopMapper;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IReviewService reviewService;

    private Cache<Long, Shop> shopLocalCache;

    private RBloomFilter<Long> shopBloomFilter;

    @PostConstruct
    public void init() {
        // 本地缓存
        shopLocalCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        // 布隆过滤器：预期 10000 条，误判率 1%
        shopBloomFilter = redissonClient.getBloomFilter("bloom:shop");
        shopBloomFilter.tryInit(10000L, 0.01);
        // 预热已有店铺 ID
        List<Shop> allShops = list();
        allShops.forEach(shop -> shopBloomFilter.add(shop.getId()));
    }

    /**
     * 查询商店（三级缓存：Caffeine → Redis → DB）
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 0. 布隆过滤器：不存在的数据直接拦截
        if (!shopBloomFilter.contains(id)) {
            return Result.fail("店铺不存在！");
        }

        // 1. 查本地缓存 Caffeine
        Shop shop = shopLocalCache.getIfPresent(id);
        if (shop != null) {
            return Result.ok(shop);
        }

        // 2. 查 Redis → DB（CacheClient 策略模式）
        shop = cacheClient.query("logicalExpire", CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在！");
        }

        // 3. 回填本地缓存
        shopLocalCache.put(id, shop);

        return Result.ok(shop);
    }

    /**
     * 更新商店
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        // 第一次删除：更新 DB 前先删缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        shopLocalCache.invalidate(id);

        updateById(shop);

        // 第二次删除：事务提交后延迟 500ms 再删，应对并发读回填脏数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 延迟 500ms 等待并发读完成回填
                LockSupport.parkNanos(500_000_000L);
                stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
                shopLocalCache.invalidate(id);
            }
        });

        return Result.ok();
    }

    @Override
    public Result followShop(Long shopId, Boolean isFollow) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String key = "follow:shop:" + userId;

        if (isFollow) {
            // 关注
            FollowShop followShop = new FollowShop();
            followShop.setUserId(userId);
            followShop.setShopId(shopId);
            followShopMapper.insert(followShop);
            stringRedisTemplate.opsForSet().add(key, shopId.toString());
        } else {
            // 取关
            followShopMapper.delete(new QueryWrapper<FollowShop>()
                    .eq("user_id", userId).eq("shop_id", shopId));
            stringRedisTemplate.opsForSet().remove(key, shopId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isFollowShop(Long shopId) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        Long count = followShopMapper.selectCount(
                new QueryWrapper<FollowShop>().eq("user_id", userId).eq("shop_id", shopId));
        return Result.ok(count > 0);
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }

    @Override
    public Result queryDashboard(Long shopId) {
        // 1. 查询店铺的所有优惠券
        List<Voucher> vouchers = voucherService.lambdaQuery()
                .eq(Voucher::getShopId, shopId).list();
        if (vouchers.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("shopId", shopId);
            empty.put("orderCount", 0);
            empty.put("revenue", 0);
            empty.put("reviewCount", 0);
            empty.put("avgRating", 0.0);
            return Result.ok(empty);
        }
        List<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toList());

        // 2. 订单统计
        long totalOrders = voucherOrderService.lambdaQuery()
                .in(VoucherOrder::getVoucherId, voucherIds).count();
        long paidOrders = voucherOrderService.lambdaQuery()
                .in(VoucherOrder::getVoucherId, voucherIds)
                .eq(VoucherOrder::getStatus, 2).count();
        long verifiedOrders = voucherOrderService.lambdaQuery()
                .in(VoucherOrder::getVoucherId, voucherIds)
                .eq(VoucherOrder::getStatus, 3).count();

        // 3. 营收估算 (已支付+已核销的订单数 * 平均面值，简化)
        long totalPayValue = vouchers.stream().mapToLong(Voucher::getPayValue).sum();
        long revenue = (paidOrders + verifiedOrders) * totalPayValue / Math.max(vouchers.size(), 1);

        // 4. 评价统计
        long reviewCount = reviewService.lambdaQuery()
                .eq(Review::getShopId, shopId).count();

        // 平均评分
        Double avgRating = reviewService.lambdaQuery()
                .eq(Review::getShopId, shopId)
                .list().stream()
                .collect(Collectors.averagingDouble(Review::getRating));

        // 5. 今日数据
        String today = DateUtil.today();
        String pvStr = stringRedisTemplate.opsForValue().get("shop:pv:" + shopId + ":" + today);
        long pv = pvStr == null ? 0 : Long.parseLong(pvStr);
        Long uv = stringRedisTemplate.opsForHyperLogLog().size("shop:uv:" + shopId + ":" + today);
        Double todaySales = stringRedisTemplate.opsForZSet().score("shop:sales:" + today, shopId.toString());

        // 6. 组装结果
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("shopId", shopId);
        dashboard.put("totalOrders", totalOrders);
        dashboard.put("paidOrders", paidOrders);
        dashboard.put("verifiedOrders", verifiedOrders);
        dashboard.put("revenue", revenue);
        dashboard.put("reviewCount", reviewCount);
        dashboard.put("avgRating", avgRating == null ? 0.0 : Math.round(avgRating * 10.0) / 10.0);
        dashboard.put("todayPv", pv);
        dashboard.put("todayUv", uv == null ? 0 : uv);
        dashboard.put("todaySales", todaySales == null ? 0 : todaySales.intValue());
        return Result.ok(dashboard);
    }
}
