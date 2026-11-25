package com.qualityminds.lazyval.integration.boundary.json;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Copy of the {@code Order} model the other scenarios generate from {@code test-api.yaml}, so the JSON-B tests read
 * and write the same document as the Jackson tests.
 */
public record Order(
        UUID id,
        Isbn isbn,
        Quantity quantity,
        EMail email,
        String currency,
        OrderDate orderDate,
        @Nullable CouponCode couponCode) {
}
