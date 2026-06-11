package com.dianpingplus;

import com.dianpingplus.utils.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import jakarta.annotation.Resource;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀 Lua 脚本原子性测试
 */
@SpringBootTest
public class SeckillScriptTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>();

    private static final Long VOUCHER_ID = 999L;
    private static final String STOCK_KEY = "seckill:stock:" + VOUCHER_ID;
    private static final String ORDER_KEY = "seckill:order:" + VOUCHER_ID;
    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10001L;

    @BeforeEach
    public void setUp() {
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/VerifyRedis.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        // 清理测试数据
        stringRedisTemplate.delete(STOCK_KEY);
        stringRedisTemplate.delete(ORDER_KEY);
    }

    @Test
    public void testSeckillSuccess() {
        // 初始化库存
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "10");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                String.valueOf(VOUCHER_ID), String.valueOf(USER_ID), String.valueOf(ORDER_ID)
        );

        assertEquals(0L, result.longValue(), "正常秒杀应返回 0");
        assertEquals("9", stringRedisTemplate.opsForValue().get(STOCK_KEY), "库存应扣减为 9");
    }

    @Test
    public void testSeckillStockInsufficient() {
        // 库存为 0
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "0");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                String.valueOf(VOUCHER_ID), String.valueOf(USER_ID), String.valueOf(ORDER_ID)
        );

        assertEquals(1L, result.longValue(), "库存不足应返回 1");
    }

    @Test
    public void testSeckillDuplicateOrder() {
        // 初始化库存
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "10");
        // 用户已下单
        stringRedisTemplate.opsForSet().add(ORDER_KEY, String.valueOf(USER_ID));

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                String.valueOf(VOUCHER_ID), String.valueOf(USER_ID), String.valueOf(ORDER_ID)
        );

        assertEquals(2L, result.longValue(), "重复下单应返回 2");
        assertEquals("10", stringRedisTemplate.opsForValue().get(STOCK_KEY), "重复下单不扣库存");
    }

    @Test
    public void testStockNotNegative() {
        // 库存为 0，验证不出现负库存
        stringRedisTemplate.opsForValue().set(STOCK_KEY, "0");

        stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                String.valueOf(VOUCHER_ID), String.valueOf(USER_ID), String.valueOf(ORDER_ID)
        );

        assertEquals("0", stringRedisTemplate.opsForValue().get(STOCK_KEY), "库存不应为负数");
    }

    @Test
    public void testBusinessExceptionMessage() {
        BusinessException exception = new BusinessException("库存不足");
        assertEquals("库存不足", exception.getMessage());
    }
}
