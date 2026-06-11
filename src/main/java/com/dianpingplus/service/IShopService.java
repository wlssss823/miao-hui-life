package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 查询商店
     * @param id
     * @return
     */
    Result queryById(Long id);

    /**
     * 更新商店
     * @param shop
     * @return
     */
    Result update(Shop shop);

    /**
     * 根据类型查询商店
     * @param typeId 商品类型
     * @param current 当前页码
     * @param x 经度
     * @param y 纬度
     * @return 商店列表
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 关注/取关店铺
     * @param shopId
     * @param isFollow true 关注, false 取关
     * @return
     */
    Result followShop(Long shopId, Boolean isFollow);

    /**
     * 查询是否关注店铺
     * @param shopId
     * @return
     */
    Result isFollowShop(Long shopId);

    /**
     * 商家后台 Dashboard
     * @param shopId
     * @return
     */
    Result queryDashboard(Long shopId);
}
