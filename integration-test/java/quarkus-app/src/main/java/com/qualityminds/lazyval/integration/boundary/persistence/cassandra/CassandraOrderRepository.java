package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
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
@Identifier("cassandra")
public class CassandraOrderRepository implements OrderRepository {

    private final CassandraOrderDao dao;
    private final QuarkusCqlSession session;
    private final CassandraMapper mapper;

    @Inject
    public CassandraOrderRepository(CassandraOrderDao dao, QuarkusCqlSession session, CassandraMapper mapper) {
        this.dao = dao;
        this.session = session;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        dao.update(mapper.toDB(order));
    }


    @Override
    public List<Order> findAll() {
        return mapper.toDomain(dao.findAll().all()).stream()
                .sorted(Comparator.comparing(order -> order.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return mapper.toDomain(dao.getById(id));
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return mapper.toDomain(dao.findByIsbn(isbn).all()).stream()
                .sorted(Comparator.comparing(order -> order.isbn().value()))
                .toList();
    }
}
