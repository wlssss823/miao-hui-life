package com.dianpingplus.controller;

import com.dianpingplus.dto.Result;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String cartKey(Long userId) {
        return "cart:" + userId;
    }

    /**
     * 添加商品到购物车（数量 +1）
     */
    @PostMapping("/{shopId}")
    public Result add(@PathVariable("shopId") Long shopId) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        String key = cartKey(userId);
        stringRedisTemplate.opsForHash().increment(key, shopId.toString(), 1);
        return Result.ok();
    }

    /**
     * 更新购物车商品数量
     */
    @PutMapping("/{shopId}/{amount}")
    public Result update(@PathVariable("shopId") Long shopId,
                         @PathVariable("amount") Integer amount) {
        if (amount <= 0) {
            return remove(shopId);
        }
        Long userId = ThreadLocalUserUtils.getUser().getId();
        stringRedisTemplate.opsForHash().put(cartKey(userId), shopId.toString(), amount.toString());
        return Result.ok();
    }

    /**
     * 从购物车移除
     */
    @DeleteMapping("/{shopId}")
    public Result remove(@PathVariable("shopId") Long shopId) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        stringRedisTemplate.opsForHash().delete(cartKey(userId), shopId.toString());
        return Result.ok();
    }

    /**
     * 查看购物车
     */
    @GetMapping
    public Result list() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(cartKey(userId));
        if (entries.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Map<String, Object>> items = new ArrayList<>();
        entries.forEach((shopId, amount) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("shopId", Long.valueOf(shopId.toString()));
            item.put("amount", Integer.valueOf(amount.toString()));
            items.add(item);
        });
        return Result.ok(items);
    }
}
