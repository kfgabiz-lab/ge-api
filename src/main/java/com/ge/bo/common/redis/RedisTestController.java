package com.ge.bo.common.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/redisTest")
@ConditionalOnProperty(
        name = "ls.redis-test-enabled",
        havingValue = "true"
)
public class RedisTestController {

    private final RedisCacheService redisCacheService;

    public RedisTestController(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    @GetMapping("/set")
    public String set(
            @RequestParam String key,
            @RequestParam String value
    ) {
        redisCacheService.setValue(key, value, Duration.ofMinutes(5));
        return "OK";
    }

    @GetMapping("/get")
    public String get(@RequestParam String key) {
        String value = redisCacheService.getValue(key);
        return value == null ? "NOT_FOUND" : value;
    }

    @GetMapping("/delete")
    public String delete(@RequestParam String key) {
        boolean deleted = redisCacheService.deleteValue(key);
        return deleted ? "DELETED" : "NOT_FOUND";
    }

    @GetMapping("/ping")
    public String ping() {
        String key = "redis:test:ping";
        String value = "pong";

        redisCacheService.setValue(key, value, Duration.ofSeconds(30));
        String result = redisCacheService.getValue(key);
        redisCacheService.deleteValue(key);

        return value.equals(result) ? "REDIS_OK" : "REDIS_FAIL";
    }
}