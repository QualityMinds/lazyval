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
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * One resource for every persistence technology under test: the {@code persistenceType} path
 * segment picks the repository. Keeps the API contract identical across backends instead of
 * duplicating the resource per store.
 */
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource implements OrderApi {

    private final RestMapper mapper;
    private final OrderRepository jpaRepository;
    private final OrderRepository cassandraRepository;
    private final OrderRepository mongoRepository;

    @Inject
    public OrderResource(RestMapper mapper,
                         @Identifier("jpa") OrderRepository jpaRepository,
                         @Identifier("cassandra") OrderRepository cassandraRepository,
                         @Identifier("mongo") OrderRepository mongoRepository) {
        this.mapper = mapper;
        this.jpaRepository = jpaRepository;
        this.cassandraRepository = cassandraRepository;
        this.mongoRepository = mongoRepository;
    }

    // Required by the JPA backend; a JTA transaction around a Cassandra or Mongo write is a no-op.
    @Transactional
    @Override
    public Order createOrder(PersistenceType persistenceType, CreateOrder createOrder) {
        var newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse(createOrder.getIsbn()),
                new Quantity(createOrder.getQuantity()),
                new EMail(createOrder.getEmail()),
                CouponCode.ofNullable(createOrder.getCouponCode()));
        repository(persistenceType).save(newOrder);
        return mapper.toDto(newOrder);
    }

    @Override
    public List<Order> findOrdersByIsbn(PersistenceType persistenceType, /*Isbn*/ String isbn) {
        return mapper.toDto(repository(persistenceType).findByIsbn(Isbn.parse(isbn)));
    }

    @Override
    public List<Order> getAllOrders(PersistenceType persistenceType) {
        return mapper.toDto(repository(persistenceType).findAll());
    }

    @Override
    public Order getOrderById(PersistenceType persistenceType, UUID id) {
        return mapper.toDto(repository(persistenceType).getById(id));
    }

    private OrderRepository repository(PersistenceType persistenceType) {
        return switch (persistenceType) {
            case JPA -> jpaRepository;
            case CASSANDRA -> cassandraRepository;
            case MONGO -> mongoRepository;
            // JDBC and R2DBC are Spring-only scenarios and answer with 501 here.
            default -> throw new UnsupportedPersistenceTypeException(persistenceType);
        };
    }
}
