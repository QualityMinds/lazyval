package com.qualityminds.lazyval.integration.domain;

import com.qualityminds.lazyval.integration.shared.Isbn;

import java.util.List;
import java.util.UUID;

public interface OrderRepository {

    void save(Order order);

    List<Order> findAll();

    Order getById(UUID id);

    List<Order> findByIsbn(Isbn isbn);
}
