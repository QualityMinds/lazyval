package com.qualityminds.lazyval.integration

import com.redis.testcontainers.RedisContainer
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.mongodb.MongoDBContainer
import spock.lang.Specification

abstract class AbstractIT extends Specification {

    static CassandraContainer cassandraContainer = new CassandraContainer("cassandra")
            .withInitScript("init.cql")
    static MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo:7")
//            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mongo")))
    static RedisContainer redisContainer = new RedisContainer("redis")

    static {
        // needs to happen here because otherwise Cassandra is not started.
        // this is due to Groovies dynamic nature
        cassandraContainer.start()
        mongoDbContainer.start()
        redisContainer.start()
    }

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        // Cassandra
        registry.add("spring.cassandra.contact-points", {
            "${cassandraContainer.host}:${cassandraContainer.getMappedPort(9042)}"
        })
        registry.add("spring.cassandra.local-datacenter", { "datacenter1" })
        // Mongo
        registry.add("spring.mongodb.port", { mongoDbContainer.getMappedPort(27017) })
        // Redis
        registry.add("spring.data.redis.port", {redisContainer.getMappedPort(6379)})
    }

}
