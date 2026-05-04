package com.qualityminds.lazyval.integration;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

@Produces(MediaType.APPLICATION_JSON)
@Path("/order")
public class OrderResource {

    @Inject
    OrderMapper mapper;

    @GET
    public List<OrderDto> getAllOrders() {
        List<Order> orders = Order.listAll();
        return mapper.toDto(orders);
    }


    @Transactional
    @POST
    public OrderDto addOrder(CreateOrderDto dto) {
        Order order = mapper.toEntity(dto);
        order.orderDate = new OrderDate(LocalDate.now());
        order.persist();
        return mapper.toDto(order);
    }
}