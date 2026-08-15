package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
@Qualifier("cassandra")
public class CassandraOrderRepository implements OrderRepository {

    private final SpringDataCassandraRepository springDataRepository;
    private final CassandraMapper mapper;

    public CassandraOrderRepository(SpringDataCassandraRepository springDataRepository, CassandraMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(Order order) {
        springDataRepository.save(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        return mapper.toDomain(springDataRepository.findAll()).stream()
                .sorted(Comparator.comparing(o -> o.isbn().value()))
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
