package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderMongoApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
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

@Produces(MediaType.APPLICATION_JSON)
public class OrderMongoResource implements OrderMongoApi {

    private final RestMapper mapper;
    private final OrderRepository repository;

    @Inject
    public OrderMongoResource(RestMapper mapper, @Identifier("mongo") OrderRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Transactional
    @Override
    public Order createOrderMongo(CreateOrder createOrder) {
        var newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
                Isbn.parse(createOrder.getIsbn()),
                new Quantity(createOrder.getQuantity()),
                new EMail(createOrder.getEmail()),
                CouponCode.ofNullable(createOrder.getCouponCode()));
        repository.save(newOrder);
        return mapper.toDto(newOrder);
    }

    @Override
    public List<Order> findOrdersByIsbnMongo(/*Isbn*/ String isbn) {
        return mapper.toDto(repository.findByIsbn(Isbn.parse(isbn)));
    }

    @Override
    public List<Order> getAllOrdersMongo() {
        return mapper.toDto(repository.findAll());
    }

    @Override
    public Order getOrderByIdMongo(UUID id) {
        return mapper.toDto(repository.getById(id));
    }
}