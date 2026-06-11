package com.dianpingplus.controller;


import com.dianpingplus.dto.Result;
import com.dianpingplus.service.IVoucherOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

@Tag(name = "秒杀订单", description = "秒杀下单、支付、核销、退款")
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Operation(summary = "秒杀下单")
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @Operation(summary = "支付订单（模拟）")
    @PostMapping("pay/{orderId}")
    public Result payOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }

    @Operation(summary = "核销订单（商家端）")
    @PostMapping("verify/{orderId}")
    public Result verifyOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.verifyOrder(orderId);
    }

    @Operation(summary = "申请退款")
    @PostMapping("refund/{orderId}")
    public Result refundOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.refundOrder(orderId);
    }
}
