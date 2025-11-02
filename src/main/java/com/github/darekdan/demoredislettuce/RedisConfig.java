package com.github.darekdan.demoredislettuce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

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
                    .allowIfBaseType(Object.class)
                    .build();

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

            GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

            RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration
                    .defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));


            // Configure a specific cache
            builder.withCacheConfiguration(ITEM_CACHE, defaultCacheConfig.entryTtl(Duration.ofSeconds(15)) // Set 15-second TTL
            );

            // Configure default for any other caches (optional)
            builder.withInitialCacheConfigurations(java.util.Collections.singletonMap("defaultCache", defaultCacheConfig));
        };
    }
}
