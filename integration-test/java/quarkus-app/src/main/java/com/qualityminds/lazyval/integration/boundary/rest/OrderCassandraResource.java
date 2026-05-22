package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.domain.EMail;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Produces(MediaType.APPLICATION_JSON)
public class OrderCassandraResource implements OrderCassandraApi {

    private final RestMapper mapper;
    private final OrderRepository repository;

    @Inject
    public OrderCassandraResource(RestMapper mapper, @Identifier("cassandra") OrderRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }


    @Override
    public Order createOrderCassandra(CreateOrder createOrder) {
        var newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse(createOrder.getIsbn()),
                new Quantity(createOrder.getQuantity()),
                new EMail(createOrder.getEmail()));
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