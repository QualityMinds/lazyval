package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.boundary.rest.model.PersistenceType;
import io.micronaut.http.annotation.Controller;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Controller
public class OrderController implements OrderApi {
    @Override
    public Order createOrder(PersistenceType persistenceType, CreateOrder createOrder) {
        return null;
    }

    @Override
    public List<@Valid Order> findOrdersByIsbn(PersistenceType persistenceType, String isbn) {
        return List.of();
    }

    @Override
    public List<@Valid Order> getAllOrders(PersistenceType persistenceType) {
        return List.of();
    }

    @Override
    public Order getOrderById(PersistenceType persistenceType, UUID id) {
        return null;
    }
}
