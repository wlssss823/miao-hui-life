package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询所有商铺类型 并存入缓存
     * @return
     */
    Result queryShopType();
}
