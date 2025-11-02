package com.github.darekdan.demoredislettuce;

import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisConfig {

    public static final String ITEM_CACHE = "itemCache";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> {
            // Configure serialization
            PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator
                    .builder()
                    .allowIfBaseType(Item.class)
                    .allowIfBaseType(String.class)
                    .build();

            RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration
                    .defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

            RedisSerializer<Item> kryoSerializer = new KryoRedisSerializer<>();
            RedisCacheConfiguration itemCacheConfig = RedisCacheConfiguration
                    .defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(15)) // Set 15-second TTL
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(kryoSerializer)); // Use KRYO

// --- 3. Apply Configurations to Builder ---
            builder
                    .cacheDefaults(defaultCacheConfig) // Apply default config to all caches
                    .withCacheConfiguration(ITEM_CACHE, itemCacheConfig); // Override for itemCache
        };
    }

}
