package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.UUID;

// @Path is declared on OrderJpaApi (the openapi-generated interface), but Liberty's JAX-RS
// implementation only treats classes annotated directly with @Path as root resources.
@Path("/order/cassandra")
@RequestScoped
public class OrderCassandraResource implements OrderCassandraApi {

    // Field injection — @RequestScoped is a normal-scoped CDI bean and requires a no-arg
    // constructor for the client proxy; field injection avoids maintaining one alongside
    // an @Inject constructor.
    @Inject
    RestMapper mapper;
    @Inject
    @Named("cassandra")
    OrderRepository repository;

    @Override
    public Order createOrderCassandra(CreateOrder createOrder) {
        var newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse(createOrder.getIsbn()),
                new Quantity(createOrder.getQuantity()),
                new EMail(createOrder.getEmail()),
                CouponCode.ofNullable(createOrder.getCouponCode()));
        repository.save(newOrder);
        return mapper.toDto(newOrder);
    }

    @Override
    public List<Order> findOrdersByIsbnCassandra(/*Isbn*/ String isbn) {
        return mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)));
    }

    @Override
    public List<Order> getAllOrdersCassandra() {
        return mapper.toDto(repository.findAll());
    }

    @Override
    public Order getOrderByIdCassandra(UUID id) {
        return mapper.toDto(repository.getById(id));
    }
}