package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.mongodb.client.model.Filters;
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

    private final PanacheMongoOrderRepository repository;
    private final MongoMapper mapper;

    @Inject
    public MongoOrderRepository(PanacheMongoOrderRepository repository, MongoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        repository.persist(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(repository.listAll()).stream()
                .sorted(Comparator.comparing(o -> o.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return repository.findByIdOptional(id).map(mapper::toDomain).orElse(null);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        // list("isbn", isbn) is currently not working because Panache-Quarkus does not properly handle codecs
        return mapper.toDomain(repository.list(Filters.eq("isbn", isbn)));
    }
}
