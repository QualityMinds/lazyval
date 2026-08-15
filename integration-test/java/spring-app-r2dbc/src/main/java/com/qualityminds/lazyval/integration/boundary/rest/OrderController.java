package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.boundary.rest.model.PersistenceType;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive counterpart to the blocking controller in {@code spring-app}: the generated interface
 * returns {@code Mono<ResponseEntity<…>>} and takes the request body as a {@code Mono}, so nothing
 * blocks between WebFlux and the R2DBC driver.
 * <p>
 * This deployment implements {@link PersistenceType#R2DBC} only. Every other type is a valid value
 * in the shared contract and answers 501.
 */
@RestController
@RequestMapping(value = "/order/{persistenceType}", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController implements OrderApi {

    private final RestMapper mapper;
    private final OrderRepository r2dbcRepository;

    public OrderController(RestMapper mapper,
                           @Qualifier("r2dbc") OrderRepository r2dbcRepository) {
        this.mapper = mapper;
        this.r2dbcRepository = r2dbcRepository;
    }

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Order>> createOrder(@PathVariable PersistenceType persistenceType,
                                                   @RequestBody Mono<CreateOrder> createOrder,
                                                   ServerWebExchange exchange) {
        return repository(persistenceType)
                .flatMap(repository -> createOrder
                        .map(dto -> com.qualityminds.lazyval.integration.domain.Order.create(
                                Isbn.parse(dto.getIsbn()),
                                new Quantity(dto.getQuantity()),
                                new EMail(dto.getEmail()),
                                CouponCode.ofNullable(dto.getCouponCode())))
                        .flatMap(repository::save))
                .map(saved -> ResponseEntity.ok(mapper.toDto(saved)));
    }

    @Override
    @GetMapping("/isbn/{isbn}")
    public Mono<ResponseEntity<Flux<Order>>> findOrdersByIsbn(@PathVariable PersistenceType persistenceType,
                                                              @PathVariable String isbn,
                                                              ServerWebExchange exchange) {
        return repository(persistenceType)
                .map(repository -> ResponseEntity.ok(
                        repository.findByIsbn(Isbn.parse(isbn)).map(mapper::toDto)));
    }

    @Override
    @GetMapping
    public Mono<ResponseEntity<Flux<Order>>> getAllOrders(@PathVariable PersistenceType persistenceType,
                                                          ServerWebExchange exchange) {
        return repository(persistenceType)
                .map(repository -> ResponseEntity.ok(repository.findAll().map(mapper::toDto)));
    }

    @Override
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Order>> getOrderById(@PathVariable PersistenceType persistenceType,
                                                    @PathVariable UUID id,
                                                    ServerWebExchange exchange) {
        return repository(persistenceType)
                .flatMap(repository -> repository.getById(id))
                .map(order -> ResponseEntity.ok(mapper.toDto(order)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Returned as a {@code Mono} rather than thrown directly so the 501 travels as an error signal
     * on the reactive chain. Throwing from the method body would also work for these signatures, but
     * only because none of them defer work — keeping it in the chain is what stays correct if an
     * operation later becomes lazier.
     */
    private Mono<OrderRepository> repository(PersistenceType persistenceType) {
        if (persistenceType == PersistenceType.R2DBC) {
            return Mono.just(r2dbcRepository);
        }
        return Mono.error(new UnsupportedPersistenceTypeException(persistenceType));
    }
}
