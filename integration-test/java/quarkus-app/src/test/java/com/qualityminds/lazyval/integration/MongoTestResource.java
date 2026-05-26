package com.qualityminds.lazyval.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.Map;

public class MongoTestResource implements QuarkusTestResourceLifecycleManager {

    final MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo:7");

    @Override
    public Map<String, String> start() {
        mongoDbContainer
                .withReplicaSet() // replicaset needed to support ACID transactions
                .start();
        return Map.of(
                "quarkus.mongodb.connection-string", mongoDbContainer.getConnectionString()
        );
    }

    @Override
    public void stop() {
        mongoDbContainer.stop();
    }
}
