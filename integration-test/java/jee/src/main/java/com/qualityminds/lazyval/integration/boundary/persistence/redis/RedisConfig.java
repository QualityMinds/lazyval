package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import io.lettuce.core.RedisClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
class RedisConfig {

    @Inject
    @ConfigProperty(name = "REDIS_URI")
    String redisUri;

    private RedisClient client;

    @PostConstruct
    void init() {
        client = RedisClient.create(redisUri);
    }

    @Produces
    @ApplicationScoped
    RedisClient redisClient() {
        return client;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.shutdown();
        }
    }
}
