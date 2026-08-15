package com.qualityminds.lazyval.integration

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import spock.lang.Specification

abstract class AbstractIT extends Specification {

    static PostgreSQLContainer postgresContainer = new PostgreSQLContainer("postgres:17-alpine")

    static {
        // needs to happen here because otherwise the container is not started.
        // this is due to Groovies dynamic nature
        postgresContainer.start()
    }

    @DynamicPropertySource
    static void r2dbcProperties(DynamicPropertyRegistry registry) {
        // R2DBC has its own URL scheme, so the JDBC url the container hands out cannot be reused
        registry.add("spring.r2dbc.url", {
            "r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.getMappedPort(5432)}/${postgresContainer.databaseName}"
        })
        registry.add("spring.r2dbc.username", { postgresContainer.username })
        registry.add("spring.r2dbc.password", { postgresContainer.password })
    }
}
