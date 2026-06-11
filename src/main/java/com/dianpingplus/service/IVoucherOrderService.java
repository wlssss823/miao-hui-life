package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀券
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单方法
     *
     * @param voucherOrder
     */
    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 支付订单（模拟）
     * @param orderId
     * @return
     */
    Result payOrder(Long orderId);

    /**
     * 核销订单（商家确认用户到店消费）
     * @param orderId
     * @return
     */
    Result verifyOrder(Long orderId);

    /**
     * 申请退款
     * @param orderId
     * @return
     */
    Result refundOrder(Long orderId);

    /**
     * 查询店铺订单列表（商家后台）
     * @param shopId 店铺ID
     * @param current 页码
     * @param status 订单状态筛选（可选）
     * @return
     */
    Result shopOrderList(Long shopId, Integer current, Integer status);
}
