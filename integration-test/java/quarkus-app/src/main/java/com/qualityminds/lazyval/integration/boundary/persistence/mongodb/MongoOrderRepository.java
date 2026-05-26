package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Identifier("mongo")
public class MongoOrderRepository implements OrderRepository {

    private final MongoMapper mapper;

    @Inject
    public MongoOrderRepository(MongoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        mapper.toDB(order).persist();
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(MongoOrder.listAll()).stream()
                .sorted(Comparator.comparing(o -> o.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return MongoOrder.findByIdOptional(id).map(db -> mapper.toDomain((MongoOrder)db)).orElse(null);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return mapper.toDomain(MongoOrder.list("isbn", isbn));
    }
}
