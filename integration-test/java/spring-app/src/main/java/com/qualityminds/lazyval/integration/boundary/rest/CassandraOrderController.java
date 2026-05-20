package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi;
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder;
import com.qualityminds.lazyval.integration.boundary.rest.model.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/order/cassandra", produces = MediaType.APPLICATION_JSON_VALUE)
public class CassandraOrderController implements OrderCassandraApi {

    private final RestMapper mapper;
    private final OrderRepository repository;

    public CassandraOrderController(RestMapper mapper,
                                    @Qualifier("cassandra") OrderRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Order> createOrderCassandra(@RequestBody CreateOrder createOrder) {
        com.qualityminds.lazyval.integration.domain.Order newOrder =
                com.qualityminds.lazyval.integration.domain.Order.create(
                        createOrder.getIsbn(), createOrder.getQuantity(), createOrder.getEmail()
                );
        repository.save(newOrder);
        return ResponseEntity.ok(mapper.toDto(newOrder));
    }

    @Override
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<List<Order>> findOrdersByIsbnCassandra(@PathVariable String isbn) {
        return ResponseEntity.ok(mapper.toDto(repository.findByIsbn(Isbn.parse(isbn))));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrdersCassandra() {
        return ResponseEntity.ok(mapper.toDto(repository.findAll()));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderByIdCassandra(@PathVariable UUID id) {
        return ResponseEntity.ok(mapper.toDto(repository.getById(id)));
    }
}
