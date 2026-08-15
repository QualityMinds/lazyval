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

import java.util.List;
import java.util.UUID;

/**
 * One controller for every persistence technology under test: the {@code persistenceType} path
 * segment picks the repository. Keeps the API contract identical across backends instead of
 * duplicating the resource per store.
 */
@RestController
@RequestMapping(value = "/order/{persistenceType}", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController implements OrderApi {

    private final RestMapper mapper;
    private final OrderRepository jpaRepository;
    private final OrderRepository cassandraRepository;
    private final OrderRepository mongoRepository;
    private final OrderRepository jdbcRepository;

    public OrderController(RestMapper mapper,
                           @Qualifier("jpa") OrderRepository jpaRepository,
                           @Qualifier("cassandra") OrderRepository cassandraRepository,
                           @Qualifier("mongo") OrderRepository mongoRepository,
                           @Qualifier("jdbc") OrderRepository jdbcRepository) {
        this.mapper = mapper;
        this.jpaRepository = jpaRepository;
        this.cassandraRepository = cassandraRepository;
        this.mongoRepository = mongoRepository;
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> createOrder(@PathVariable PersistenceType persistenceType,
                                             @RequestBody CreateOrder createOrder) {
        com.qualityminds.lazyval.integration.domain.Order newOrder =
                com.qualityminds.lazyval.integration.domain.Order.create(
                        Isbn.parse(createOrder.getIsbn()),
                        new Quantity(createOrder.getQuantity()),
                        new EMail(createOrder.getEmail()),
                        CouponCode.ofNullable(createOrder.getCouponCode())
                );
        repository(persistenceType).save(newOrder);
        return ResponseEntity.ok(mapper.toDto(newOrder));
    }

    @Override
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<List<Order>> findOrdersByIsbn(@PathVariable PersistenceType persistenceType,
                                                        @PathVariable String isbn) {
        return ResponseEntity.ok(mapper.toDto(repository(persistenceType).findByIsbn(Isbn.parse(isbn))));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(@PathVariable PersistenceType persistenceType) {
        return ResponseEntity.ok(mapper.toDto(repository(persistenceType).findAll()));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable PersistenceType persistenceType,
                                              @PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toDto(repository(persistenceType).getById(id)));
    }

    private OrderRepository repository(PersistenceType persistenceType) {
        return switch (persistenceType) {
            case JPA -> jpaRepository;
            case CASSANDRA -> cassandraRepository;
            case MONGO -> mongoRepository;
            case JDBC -> jdbcRepository;
            // R2DBC lives in the spring-app-r2dbc scenario and answers with 501 here.
            default -> throw new UnsupportedPersistenceTypeException(persistenceType);
        };
    }
}
