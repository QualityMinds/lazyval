package com.qualityminds.lazyval.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import spock.lang.Specification

abstract class AbstractIT extends Specification {

    static CassandraContainer cassandraContainer = new CassandraContainer("cassandra")
            .withInitScript("init.cql")

    static {
        // needs to happen here because otherwise Cassandra is not started.
        // this is due to Groovies dynamic nature
        cassandraContainer.start()
    }

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points", {
            "${cassandraContainer.host}:${cassandraContainer.getMappedPort(9042)}"
        })
        registry.add("spring.cassandra.local-datacenter", { "datacenter1" })
    }
}
