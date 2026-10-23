package com.qualityminds.lazyval.integration.boundary.persistence.redis

import io.lettuce.core.RedisClient
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class RedisConfig @Inject constructor(
    @ConfigProperty(name = "REDIS_URI")
    private val redisUri: String
) {

    private lateinit var client: RedisClient

    @PostConstruct
    fun init() {
        client = RedisClient.create(redisUri)
    }

    @Produces
    @ApplicationScoped
    fun redisClient(): RedisClient = client

    @PreDestroy
    fun close() {
        if (::client.isInitialized) {
            client.shutdown()
        }
    }
}
