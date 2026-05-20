package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static java.util.stream.StreamSupport.stream;

@Repository
@Qualifier("jpa")
class JpaOrderRepository implements OrderRepository {

    private final SpringDataRepository springDataRepository;
    private final JpaMapper mapper;

    JpaOrderRepository(SpringDataRepository springDataRepository, JpaMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        springDataRepository.save(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(stream(springDataRepository.findAll().spliterator(), false).toList());
    }

    @Override
    public Order getById(UUID id) {
        return mapper.toDomain(springDataRepository.findById(id).orElse(null));
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        return mapper.toDomain(springDataRepository.findByIsbn(isbn));
    }
}
