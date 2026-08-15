package com.qualityminds.lazyval.integration.domain;

import com.qualityminds.lazyval.integration.shared.Isbn;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive counterpart to the blocking port in the {@code spring-app} scenario. The whole point of
 * this module is that nothing blocks between the HTTP boundary and the driver, so the port itself is
 * reactive rather than hiding a {@code block()} in the adapter.
 */
public interface OrderRepository {

    Mono<Order> save(Order order);

    Flux<Order> findAll();

    Mono<Order> getById(UUID id);

    Flux<Order> findByIsbn(Isbn isbn);
}
