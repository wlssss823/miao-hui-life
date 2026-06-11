package com.dianpingplus.service.impl;

import cn.hutool.json.JSONUtil;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.ShopType;
import com.dianpingplus.mapper.ShopTypeMapper;
import com.dianpingplus.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.dianpingplus.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有商铺类型 并存入缓存
     * @return
     */
    @Override
    public Result queryShopType() {
        List<String> jsonList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);

        if(jsonList != null && !jsonList.isEmpty()){
            //若缓存命中，直接返回
            ArrayList<ShopType> shopTypes = new ArrayList<>();
            for (String json : jsonList) {
                ShopType shopType = JSONUtil.toBean(json, ShopType.class);
                shopTypes.add(shopType);
            }

            return Result.ok(shopTypes);
        }
        //若未命中，从数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //检查是否查询到
        if(shopTypes == null || shopTypes.isEmpty()){
            return Result.fail("未查询到商铺类型");
        }
        //存入缓存
        for(ShopType shopType : shopTypes){
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }

        return Result.ok(shopTypes);
    }
}
