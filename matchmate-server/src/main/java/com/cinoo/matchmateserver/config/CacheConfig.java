package com.cinoo.matchmateserver.config;

import com.cinoo.matchmateserver.infrastructure.cache.CacheNames;
import org.apache.commons.lang3.StringUtils;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class CacheConfig {

    // Cached JSON contains fully qualified class names, so package moves require a new namespace.
    private static final String CACHE_PREFIX = "matchmate:cache:v3:";

    @Bean
    public RedisSerializer<Object> cacheValueSerializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.cinoo.matchmateserver.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.util.")
                .allowIfSubTypeIsArray()
                .build();
        JsonMapper objectMapper = JsonMapper.builder()
                .activateDefaultTypingAsProperty(
                        typeValidator,
                        DefaultTyping.NON_FINAL,
                        "@class"
                )
                .build();
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(
            RedisSerializer<Object> cacheValueSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_PREFIX + cacheName + ":")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                cacheValueSerializer
                        )
                )
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(
            RedisCacheConfiguration defaultConfiguration) {
        return builder -> builder
                .withCacheConfiguration(
                        CacheNames.TAG_CATEGORIES,
                        defaultConfiguration.entryTtl(Duration.ofHours(6))
                )
                .withCacheConfiguration(
                        CacheNames.USER_TAGS,
                        defaultConfiguration.entryTtl(Duration.ofMinutes(30))
                )
                .withCacheConfiguration(
                        CacheNames.USER_VIEWS,
                        defaultConfiguration.entryTtl(Duration.ofMinutes(10))
                )
                .withCacheConfiguration(
                        CacheNames.USER_RECOMMENDATIONS,
                        defaultConfiguration.entryTtl(Duration.ofMinutes(4))
                )
                .withCacheConfiguration(
                        CacheNames.USER_SEARCHES,
                        defaultConfiguration.entryTtl(Duration.ofMinutes(4))
                );
    }

    @Bean
    public RedissonAutoConfigurationCustomizer blankRedisCredentialsCustomizer(
            DataRedisProperties redisProperties) {
        return config -> {
            if (StringUtils.isBlank(redisProperties.getPassword())) {
                config.setPassword(null);
            }
            if (StringUtils.isBlank(redisProperties.getUsername())) {
                config.setUsername(null);
            }
        };
    }
}
