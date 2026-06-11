package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

    /**
     * 查询关注店铺的优惠券列表
     * @return
     */
    Result queryFollowShopVouchers();

    /**
     * 秒杀商品详情（含缓存，静态化数据）
     * @param voucherId
     * @return
     */
    Result querySeckillDetail(Long voucherId);
}
