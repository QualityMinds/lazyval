package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration;
import com.qualityminds.lazyval.integration.domain.Order;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
class RedisCacheConfig {

    /**
     * Picked up by Spring Boot as the default for every Redis cache.
     * <p>
     * Values are cached as JSON through the application's mapper, which carries the Lazyval Jackson
     * module; keys go through the generated cache-key converters, so a domain-primitive key such as
     * {@code Isbn} reads as its wrapped value rather than its {@code toString()}.
     */
    @Bean
    RedisCacheConfiguration cacheConfiguration(ObjectMapper jsonMapper) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, Order.class)));
        configuration.configureKeyConverters(LazyvalSpringDataConfiguration.RedisCacheKeys::registerAll);
        return configuration;
    }
}
