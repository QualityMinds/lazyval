package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import com.datastax.oss.driver.api.core.config.DriverConfigLoader
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.InetSocketAddress

@ApplicationScoped
class CassandraConfig @Inject constructor(
    @ConfigProperty(name = "CASSANDRA_CONTACT_POINTS")
    private val contactPoints: String,
    @ConfigProperty(name = "CASSANDRA_LOCAL_DATACENTER", defaultValue = "datacenter1")
    private val localDatacenter: String
) {

    private var session: CqlSession? = null

    @PostConstruct
    fun init() {
        val (host, portStr) = contactPoints.split(":")
        session = CqlSession.builder()
            .addTypeCodecs(*LazyvalCassandraCodecs.all())
            .addContactPoint(InetSocketAddress(host, portStr.toInt()))
            .withLocalDatacenter(localDatacenter)
            .withKeyspace("jee")
            .withConfigLoader(
                DriverConfigLoader.programmaticBuilder()
                    .withBoolean(DefaultDriverOption.METADATA_SCHEMA_ENABLED, false)
                    .build()
            )
            .build()
    }

    @Produces
    @ApplicationScoped
    fun cqlSession(): CqlSession = session!!

    @PreDestroy
    fun close() {
        session?.close()
    }
}
