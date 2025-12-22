package de.qualityminds.lazyval.integration;

public record CreateOrderDto(
        String isbn,
        int quantity,
        String email
){}
