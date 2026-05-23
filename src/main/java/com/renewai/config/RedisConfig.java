package com.renewai.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache Configuration
 *
 * Configures a RedisCacheManager with:
 * - JSON serialization (avoids JDK serialization issues)
 * - Per-cache TTL settings (so stale data auto-expires as a safety net)
 * - String keys for human-readable Redis key names
 */
@Configuration
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host}")
    private String redisHost;

    @org.springframework.beans.factory.annotation.Value("${spring.data.redis.ssl.enabled}")
    private boolean redisSsl;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        System.out.println("===========================================");
        System.out.println("   [Redis] Initialization Check");
        System.out.println("   Host: " + redisHost);
        System.out.println("   SSL Enabled: " + redisSsl);
        System.out.println("===========================================");
        
        // Build a custom ObjectMapper that handles LocalDate/LocalDateTime correctly
        ObjectMapper redisObjectMapper = new ObjectMapper();
        redisObjectMapper.registerModule(new JavaTimeModule());
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Embed type info so Redis knows what class to deserialize back to
        redisObjectMapper.activateDefaultTyping(
                redisObjectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        // Default config: 30 min TTL, JSON values, String keys
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        // Per-cache TTL overrides (dashboard data refreshes every 10 min)
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("dashboardSummary",   defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("renewalTrends",      defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("revenueTrends",      defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("policyDistribution", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("aiInsights",         defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("conversionFunnel",   defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("policiesList",       defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Catches any Redis error (serialization, connection, etc.) and logs it
     * instead of letting it propagate and crash the HTTP request.
     * The method will then execute normally against the database as a fallback.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("[Redis] Cache GET error on cache='{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("[Redis] Cache PUT error on cache='{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("[Redis] Cache EVICT error on cache='{}' key='{}': {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                log.warn("[Redis] Cache CLEAR error on cache='{}': {}", cache.getName(), e.getMessage());
            }
        };
    }
}
