package com.ge.bo.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setValue(String key, String value) {
        redisTemplate.opsForValue()
                .set(key, value, Duration.ofMinutes(10));
    }

    public void setValue(String key, String value, Duration ttl) {
        redisTemplate.opsForValue()
                .set(key, value, ttl);
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue()
                .get(key);
    }

    public boolean deleteValue(String key) {
        return redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
