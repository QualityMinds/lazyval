package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MongoConfig {


    @Inject
    @ConfigProperty(name = "MONGODB_CONNECTION_URL")
    String connectionUrl;

    private MongoClient client;

    @PostConstruct
    public void init() {
        CodecRegistry registry = CodecRegistries.fromRegistries(
                LazyvalMongoCodecs.asRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder()
                        // in a plain mongo project you might use .automatic(true)
                        // instead we scan the mongo boundary package
                        .register(MongoOrder.class.getPackageName())
                        .build())
        );

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionUrl))
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(registry)
                .build();

        client = MongoClients.create(settings);
    }

    @Produces
    @ApplicationScoped
    public MongoClient mongoClient() {
        return client;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
