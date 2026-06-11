package com.dianpingplus.interceptor;

import cn.hutool.core.date.DateUtil;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 店铺 PV/UV 统计拦截器，拦截 GET /shop/{id} 自动记录
 */
public class PvUvInterceptor implements HandlerInterceptor {

    private static final Pattern SHOP_PATH_PATTERN = Pattern.compile("^/shop/(\\d+)$");

    private final StringRedisTemplate stringRedisTemplate;

    public PvUvInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅统计 GET /shop/{id}
        if (!"GET".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        Matcher matcher = SHOP_PATH_PATTERN.matcher(path);
        if (!matcher.matches()) return true;

        Long shopId = Long.valueOf(matcher.group(1));
        String today = DateUtil.today();

        // PV：每日计数 +1
        stringRedisTemplate.opsForValue().increment("shop:pv:" + shopId + ":" + today);

        // UV：登录用户记入 HyperLogLog
        UserDTO user = ThreadLocalUserUtils.getUser();
        if (user != null) {
            stringRedisTemplate.opsForHyperLogLog().add("shop:uv:" + shopId + ":" + today, user.getId().toString());
        }

        return true;
    }
}
