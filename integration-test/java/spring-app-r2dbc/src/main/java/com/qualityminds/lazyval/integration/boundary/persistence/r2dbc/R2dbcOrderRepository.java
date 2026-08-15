package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.UUID;

@Repository
@Qualifier("r2dbc")
public class R2dbcOrderRepository implements OrderRepository {

    private final SpringDataR2dbcRepository springDataRepository;
    private final R2dbcMapper mapper;

    public R2dbcOrderRepository(SpringDataR2dbcRepository springDataRepository, R2dbcMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Order> save(Order order) {
        return springDataRepository.save(mapper.toDB(order)).map(mapper::toDomain);
    }

    @Override
    public Flux<Order> findAll() {
        // sorted by ISBN like the other scenarios, so the ITs can assert a stable order
        return springDataRepository.findAll()
                .map(mapper::toDomain)
                .sort(Comparator.comparing(order -> order.isbn().value()));
    }

    @Override
    public Mono<Order> getById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<Order> findByIsbn(Isbn isbn) {
        return springDataRepository.findByIsbn(isbn).map(mapper::toDomain);
    }
}
