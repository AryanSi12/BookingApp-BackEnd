package com.example.demo.Configurations;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisDebugConfig {
    private final RedisConnectionFactory redisConnectionFactory;

    public RedisDebugConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @PostConstruct
    public void checkRedisConnection() {
        System.out.println("Redis Connection Factory class: " + redisConnectionFactory.getClass().getName());
        try {
            redisConnectionFactory.getConnection().ping();
            System.out.println("Successfully pinged Redis");
        } catch (Exception e) {
            System.err.println("Failed to ping Redis: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
