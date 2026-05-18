package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetSocketAddress;

@ApplicationScoped
public class CassandraConfig {


    @Inject
    @ConfigProperty(name = "CASSANDRA_CONTACT_POINTS")
    String contactPoints;

    @Inject
    @ConfigProperty(name = "CASSANDRA_LOCAL_DATACENTER", defaultValue = "datacenter1")
    String localDatacenter;

    private CqlSession session;

    @PostConstruct
    public void init() {
        String[] parts = contactPoints.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        session = CqlSession.builder()
                .addTypeCodecs(LazyvalCassandraCodecs.all())
                .addContactPoint(new InetSocketAddress(host, port))
                .withLocalDatacenter(localDatacenter)
                .withKeyspace("jee")
                .withConfigLoader(
                        DriverConfigLoader.programmaticBuilder()
                                .withBoolean(DefaultDriverOption.METADATA_SCHEMA_ENABLED, false)
                                .build())
                .build();
    }

    @Produces
    @ApplicationScoped
    public CqlSession cqlSession() {
        return session;
    }

    @PreDestroy
    void close() {
        if (session != null) {
            session.close();
        }
    }
}
