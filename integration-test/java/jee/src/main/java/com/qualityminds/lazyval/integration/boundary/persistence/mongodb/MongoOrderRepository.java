package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;

@ApplicationScoped
@Named("mongo")
public class MongoOrderRepository implements OrderRepository {

    private final MongoClient client;
    private final MongoMapper mapper;

    @Inject
    public MongoOrderRepository(MongoClient client, MongoMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    private MongoCollection<MongoOrder> orders() {
        return client.getDatabase("jee").getCollection("orders", MongoOrder.class);
    }

    @Override
    public void save(Order order) {
        MongoOrder doc = mapper.toDB(order);
        // ClientSession + withTransaction gives true Mongo transactional semantics on writes.
        // Note: requires the Mongo deployment to be a replica set (see AbstractLibertyIT).
        try (ClientSession session = client.startSession()) {
            session.withTransaction(() -> {
                orders().insertOne(session, doc);
                return null;
            });
        }
    }

    @Override
    public List<Order> findAll() {
        List<MongoOrder> all = orders().find().into(new ArrayList<>());
        return all.stream()
                .map(mapper::toDomain)
                .sorted(Comparator.comparing(o -> o.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        MongoOrder found = orders().find(eq("_id", id)).first();
        return found == null ? null : mapper.toDomain(found);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        // codec-aware filter: the registered IsbnCodec encodes the value object to a plain String,
        // so Filters.eq picks up the same representation that was stored.
        List<MongoOrder> matches = orders().find(eq("isbn", isbn)).into(new ArrayList<>());
        return matches.stream()
                .map(mapper::toDomain)
                .sorted(Comparator.comparing(o -> o.isbn().value()))
                .toList();
    }
}