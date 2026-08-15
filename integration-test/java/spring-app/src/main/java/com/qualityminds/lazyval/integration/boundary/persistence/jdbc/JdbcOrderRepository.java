package com.qualityminds.lazyval.integration.boundary.persistence.jdbc;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static java.util.stream.StreamSupport.stream;

@Repository
@Qualifier("jdbc")
class JdbcOrderRepository implements OrderRepository {

    private final SpringDataJdbcRepository springDataRepository;
    private final JdbcMapper mapper;

    JdbcOrderRepository(SpringDataJdbcRepository springDataRepository, JdbcMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        springDataRepository.save(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(stream(springDataRepository.findAll().spliterator(), false).toList()).stream()
                .sorted(Comparator.comparing(order -> order.isbn().value()))
                .toList();
    }

    @Override
    public Order getById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain).orElse(null);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return mapper.toDomain(springDataRepository.findByIsbn(isbn));
    }
}
