package com.qualityminds.lazyval.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.cassandra.CassandraContainer;

import java.util.Map;

public class CassandraTestResource implements QuarkusTestResourceLifecycleManager {

    final CassandraContainer cassandraContainer = new CassandraContainer("cassandra");

    @Override
    public Map<String, String> start() {
        cassandraContainer.withInitScript("init.cql").start();
        return Map.of(
                "quarkus.cassandra.contact-points", cassandraContainer.getHost() + ":" + cassandraContainer.getMappedPort(9042)
        );
    }

    @Override
    public void stop() {
        cassandraContainer.stop();
    }
}
