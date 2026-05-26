package com.qualityminds.lazyval.integation

import org.slf4j.LoggerFactory
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.mongodb.MongoDBContainer
import spock.lang.Specification

import java.time.Duration

abstract class AbstractLibertyIT extends Specification {

    private static Network network = Network.newNetwork()

    protected static int PORT = 9080
    protected static GenericContainer liberty

    private static CassandraContainer cassandraContainer = new CassandraContainer("cassandra")
            .withInitScript("init.cql").withNetwork(network).withNetworkAliases("cassandra")
//            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("cassandra")))
    private static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:7")
            .withReplicaSet() // replicaset needed to support ACID transactions
            .withNetwork(network).withNetworkAliases("mongo")
//            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mongo")))

    static {
        cassandraContainer.start()
        mongoContainer.start()

        // Use the Docker-internal hostname and native port (not the host-mapped port)
        def cassandraContactPoint = "cassandra:9042"
        def mongoDbConnection = "mongodb://mongo:27017"

        liberty = new GenericContainer<>("icr.io/appcafe/open-liberty:full-java17-openj9-ubi-minimal")
                .withFileSystemBind(
                        "target/integration-test-java-jee-app",
                        "/config/apps/integration-test-java-jee-app",
                        BindMode.READ_ONLY
                )
                .withFileSystemBind(
                        "src/main/liberty/config/server.xml",
                        "/config/server.xml",
                        BindMode.READ_ONLY
                )
                .withNetwork(network)
                .withExposedPorts(PORT)
                .withEnv("CASSANDRA_CONTACT_POINTS", cassandraContactPoint)
                .withEnv("CASSANDRA_LOCAL_DATACENTER", "datacenter1")
                .withEnv("MONGODB_CONNECTION_URL", mongoDbConnection)
                .waitingFor(Wait.forLogMessage(".*CWWKZ0001I.* integration-test-java-jee-app .*", 1))
                .withStartupTimeout(Duration.ofMinutes(1))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("liberty")))

        liberty.start()
    }
}
