package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;

public record OrderDto(
    Long id,
    Isbn isbn,
    Quantity quantity,
    EMail email
){}

