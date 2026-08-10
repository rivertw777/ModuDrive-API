package com.moduDrive.common.infrastructure.redis.config;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig {

    @Bean
    public RedisRepository redisRepository(StringRedisTemplate redisTemplate) {
        return new RedisRepository(redisTemplate);
    }
}
