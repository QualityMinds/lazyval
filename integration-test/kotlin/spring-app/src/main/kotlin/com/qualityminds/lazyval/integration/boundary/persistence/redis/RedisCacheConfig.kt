package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration
import com.qualityminds.lazyval.integration.domain.Order
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableCaching
internal class RedisCacheConfig {

    /**
     * Picked up by Spring Boot as the default for every Redis cache.
     *
     * Values are cached as JSON through the application's mapper, which carries the Lazyval Jackson
     * module; keys go through the generated cache-key converters, so a domain-primitive key such as
     * `Isbn` reads as its wrapped value rather than its `toString()`.
     */
    @Bean
    fun cacheConfiguration(jsonMapper: ObjectMapper): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(SerializationPair.fromSerializer(JacksonJsonRedisSerializer(jsonMapper, Order::class.java)))
            .also { it.configureKeyConverters(LazyvalSpringDataConfiguration.RedisCacheKeys::registerAll) }
}
