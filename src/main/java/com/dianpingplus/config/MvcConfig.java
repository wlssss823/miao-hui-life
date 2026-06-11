package com.dianpingplus.config;

import com.dianpingplus.interceptor.LoginInterceptor;
import com.dianpingplus.interceptor.PvUvInterceptor;
import com.dianpingplus.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/blog/{id}",
                        "/user/code",
                        "/user/login",
                        "/user/{id}",
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/webjars/**")
                .order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // PV/UV 统计（order=2 确保 RefreshTokenInterceptor 已将用户信息写入 ThreadLocal）
        registry.addInterceptor(new PvUvInterceptor(stringRedisTemplate))
                .addPathPatterns("/shop/**")
                .order(2);
    }
}
