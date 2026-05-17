package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

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
@Identifier("jpa")
public class JpaOrderRepository implements OrderRepository {

    private final PanacheRepository repository;
    private final JpaMapper mapper;

    @Inject
    public JpaOrderRepository(PanacheRepository repository, JpaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        repository.persist(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(repository.findAll().list()).stream()
                .sorted(Comparator.comparing(order -> order.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return mapper.toDomain(repository.findById(id));
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return mapper.toDomain(repository.findByIsbn(isbn));
    }
}
