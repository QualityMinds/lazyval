package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.boundary.persistence.LazyvalSpringDataConfiguration
import com.qualityminds.lazyval.integration.domain.Order
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
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

    /**
     * With Lettuce, Spring Data Redis writes to the cache asynchronously by default: `put` returns
     * before the entry is stored, so an immediate second lookup may miss. Immediate writes make a cached
     * value visible as soon as the `@Cacheable` method returns.
     * Only needed for testing.
     */
    @Bean
    fun immediateCacheWrites(connectionFactory: RedisConnectionFactory) = RedisCacheManagerBuilderCustomizer { builder ->
        builder.cacheWriter(RedisCacheWriter.create(connectionFactory) { it.immediateWrites() })
    }
}
