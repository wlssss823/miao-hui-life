package com.dianpingplus.controller;

import cn.hutool.core.date.DateUtil;
import com.dianpingplus.dto.Result;
import com.dianpingplus.service.IShopService;
import com.dianpingplus.service.IVoucherOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.*;

@Tag(name = "商家后台", description = "商家管理后台：数据看板、订单管理")
@RestController
@RequestMapping("/shop/stats")
public class ShopStatsController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Operation(summary = "查看店铺今日 PV/UV")
    @GetMapping("/{shopId}")
    public Result shopStats(@PathVariable("shopId") Long shopId) {
        String today = DateUtil.today();
        String pv = stringRedisTemplate.opsForValue().get("shop:pv:" + shopId + ":" + today);
        Long uv = stringRedisTemplate.opsForHyperLogLog().size("shop:uv:" + shopId + ":" + today);

        Map<String, Object> stats = new HashMap<>();
        stats.put("shopId", shopId);
        stats.put("pv", pv == null ? 0 : Long.parseLong(pv));
        stats.put("uv", uv);
        return Result.ok(stats);
    }

    @Operation(summary = "查看今日销量排行 Top10")
    @GetMapping("/sales/top")
    public Result salesTop() {
        String today = DateUtil.today();
        Set<String> top = stringRedisTemplate.opsForZSet()
                .reverseRangeByScore("shop:sales:" + today, 0, Double.MAX_VALUE, 0, 10);
        return Result.ok(top == null ? Collections.emptyList() : new ArrayList<>(top));
    }

    @Operation(summary = "商家 Dashboard 数据看板")
    @GetMapping("/dashboard/{shopId}")
    public Result dashboard(@PathVariable("shopId") Long shopId) {
        return shopService.queryDashboard(shopId);
    }

    @Operation(summary = "店铺订单管理列表（商家后台）")
    @GetMapping("/orders/{shopId}")
    public Result shopOrders(@PathVariable("shopId") Long shopId,
                             @RequestParam(value = "current", defaultValue = "1") Integer current,
                             @RequestParam(value = "status", required = false) Integer status) {
        return voucherOrderService.shopOrderList(shopId, current, status);
    }
}
