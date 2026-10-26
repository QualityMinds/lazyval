package com.qualityminds.lazyval.integration;

import com.redis.testcontainers.RedisContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

public class RedisTestResource implements QuarkusTestResourceLifecycleManager {

    final RedisContainer redisContainer = new RedisContainer("redis");

    @Override
    public Map<String, String> start() {
        redisContainer.start();
        return Map.of("quarkus.redis.hosts", redisContainer.getRedisURI());
    }

    @Override
    public void stop() {
        redisContainer.stop();
    }
}
