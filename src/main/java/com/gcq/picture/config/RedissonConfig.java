package com.gcq.picture.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 创建Redisson配置
        Config config = new Config();
        // 这里假设使用单机Redis，需要根据实际情况修改地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");

        // 创建RedissonClient实例
        return Redisson.create(config);
    }
}