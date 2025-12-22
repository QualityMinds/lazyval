package de.qualityminds.lazyval.integration;

public record OrderDto(
    Long id,
    String isbn,
    int quantity,
    String email
){}

