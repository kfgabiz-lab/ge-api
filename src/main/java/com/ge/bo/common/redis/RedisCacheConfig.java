package com.ge.bo.common.redis;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ge.bo.dto.PageDataResponse;
import com.ge.bo.dto.SearchManageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "ls.redis-enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .disableCachingNullValues()
                        .entryTtl(Duration.ofMinutes(30))
                        .computePrefixWith(
                                cacheName ->
                                        "nahp"
                                                + ":cache:"
                                                + cacheName
                                                + "::"
                        )
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new StringRedisSerializer()
                                        )
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new GenericJackson2JsonRedisSerializer()
                                                        .configure(om -> om.registerModule(new JavaTimeModule()))
                                                        .configure(om -> om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                                        )
                        );

        RedisCacheConfiguration searchManageConfig =
                defaultConfig
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new Jackson2JsonRedisSerializer<>(
                                                        new ObjectMapper()
                                                                .registerModule(new JavaTimeModule())
                                                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                                                        SearchManageResponse.class
                                                )
                                        )
                        );

        RedisCacheConfiguration productDataConfig =
                defaultConfig
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new Jackson2JsonRedisSerializer<>(
                                                        new ObjectMapper()
                                                                .registerModule(new JavaTimeModule())
                                                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                                                        PageDataResponse.class
                                                )
                                        )
                        );

        // contentsData(blog-data/press-data/articles-data) — product와 동일한 write-through 원칙,
        // 값 타입도 동일한 PageDataResponse라 직렬화기를 재사용한다.
        RedisCacheConfiguration contentsDataConfig =
                defaultConfig
                        .entryTtl(Duration.ofMinutes(30))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(
                                                new Jackson2JsonRedisSerializer<>(
                                                        new ObjectMapper()
                                                                .registerModule(new JavaTimeModule())
                                                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                                                        PageDataResponse.class
                                                )
                                        )
                        );

        Map<String, RedisCacheConfiguration> cacheConfigurations =
                Map.of(
                        "users",
                        defaultConfig.entryTtl(Duration.ofMinutes(10)),

                        "commonCodes",
                        defaultConfig.entryTtl(Duration.ofHours(6)),

                        "fileInfo",
                        defaultConfig.entryTtl(Duration.ofMinutes(30)),

                        "searchManage",
                        searchManageConfig,

                        "productData",
                        productDataConfig,

                        "contentsData",
                        contentsDataConfig
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
