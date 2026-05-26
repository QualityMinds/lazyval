package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import org.bson.UuidRepresentation
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class MongoConfig @Inject constructor(
    @ConfigProperty(name = "MONGODB_CONNECTION_URL")
    private val connectionUrl: String
) {

    private lateinit var client: MongoClient

    @PostConstruct
    fun init() {
        val registry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(LazyvalMongoCodecs()),
            CodecRegistries.fromProviders(
                PojoCodecProvider.builder()
                    // in a plain mongo project you might use .automatic(true)
                    // instead we scan the mongo boundary package
                    .register(MongoOrder::class.java.packageName)
                    .build()
            )
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionUrl))
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .codecRegistry(registry)
            .build()

        client = MongoClients.create(settings)
    }

    @Produces
    @ApplicationScoped
    fun mongoClient(): MongoClient = client

    @PreDestroy
    fun close() {
        if (::client.isInitialized) {
            client.close()
        }
    }
}
