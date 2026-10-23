package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;

import java.util.Currency;
import java.util.UUID;

/**
 * Stored as one JSON value per order. Quarkus Redis has no object-to-hash mapping: a value type
 * without a {@code Codec} bean is written as JSON through the application's {@code ObjectMapper},
 * which carries the Lazyval Jackson module, so the domain-primitives end up as JSON scalars.
 */
record RedisOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, Currency currency, OrderDate orderDate,
                  @Nullable CouponCode couponCode) {
}
