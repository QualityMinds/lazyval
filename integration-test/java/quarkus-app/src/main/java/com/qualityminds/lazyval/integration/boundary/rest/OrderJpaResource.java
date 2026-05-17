package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderJpaApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Produces(MediaType.APPLICATION_JSON)
public class OrderJpaResource implements OrderJpaApi {

    private final RestMapper mapper;
    private final OrderRepository repository;

    @Inject
    public OrderJpaResource(RestMapper mapper, @Identifier("jpa") OrderRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Transactional
    @Override
    public Order createOrderJpa(CreateOrder createOrder) {
        var newOrder = com.qualityminds.lazyval.integration.domain.Order.create(createOrder.getIsbn(), createOrder.getQuantity(), createOrder.getEmail());
        repository.save(newOrder);
        return mapper.toDto(newOrder);
    }

    @Override
    public List<Order> findOrdersByIsbnJpa(/*Isbn*/ String isbn) {
        return mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)));
    }

    @Override
    public List<Order> getAllOrdersJpa() {
        return mapper.toDto(repository.findAll());
    }

    @Override
    public Order getOrderByIdJpa(UUID id) {
        return mapper.toDto(repository.getById(id));
    }
}