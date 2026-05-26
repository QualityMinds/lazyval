package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PanacheMongoOrderRepository implements PanacheMongoRepositoryBase<MongoOrder, UUID> {

}
