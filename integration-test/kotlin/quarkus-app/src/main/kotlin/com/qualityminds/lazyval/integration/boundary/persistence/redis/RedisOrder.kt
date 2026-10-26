package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import java.util.*

/**
 * Stored as one JSON value per order. Quarkus Redis has no object-to-hash mapping: a value type
 * without a `Codec` bean is written as JSON through the application's `ObjectMapper`, which carries
 * the Lazyval Jackson module, so the domain-primitives end up as JSON scalars.
 */
data class RedisOrder(
    val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val currency: Currency,
    val orderDate: OrderDate,
    val couponCode: CouponCode? = null
)
