package com.dianpingplus.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * 创建Redisson配置对象，然后交给IOC管理
     *
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        // 获取Redisson配置对象
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + this.host + ":" + this.port)
                .setDatabase(this.database);
        return Redisson.create(config);
    }
}
