package com.qualityminds.lazyval.integration.domain;

import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;

import java.util.UUID;

public record Order(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate) {

    public static Order create(Isbn isbn, Quantity quantity, EMail email){
        return new Order(UUID.randomUUID(), isbn, quantity, email, OrderDate.now());
    }
}
