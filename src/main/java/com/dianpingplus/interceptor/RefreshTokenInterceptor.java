package com.dianpingplus.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.utils.RedisConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 登录校验并刷新token
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute, for type and/or instance evaluation
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }

        //查询Redis用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if(userMap.isEmpty()){
            return true;
        }

        //保存到ThreadLocal
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        ThreadLocalUserUtils.putUser(user);

        //刷新Token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //放行
        return true;
    }
}
