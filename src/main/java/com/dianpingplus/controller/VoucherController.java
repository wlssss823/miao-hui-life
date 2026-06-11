package com.dianpingplus.controller;


import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Voucher;
import com.dianpingplus.service.IVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Tag(name = "优惠券", description = "优惠券查询、秒杀详情、店铺上新")
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    @Operation(summary = "新增普通券")
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    @Operation(summary = "新增秒杀券")
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    @Operation(summary = "查询店铺优惠券列表")
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }

    @Operation(summary = "查询关注店铺的优惠券上新")
    @GetMapping("/follow")
    public Result queryFollowShopVouchers() {
        return voucherService.queryFollowShopVouchers();
    }

    @Operation(summary = "秒杀商品详情（含缓存）")
    @GetMapping("/seckill/detail/{voucherId}")
    public Result querySeckillDetail(@PathVariable("voucherId") Long voucherId) {
        return voucherService.querySeckillDetail(voucherId);
    }
}
