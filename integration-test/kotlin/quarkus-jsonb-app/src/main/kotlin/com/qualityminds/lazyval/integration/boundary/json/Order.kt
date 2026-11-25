package com.qualityminds.lazyval.integration.boundary.json

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.json.bind.annotation.JsonbCreator
import jakarta.json.bind.annotation.JsonbProperty
import java.util.UUID

/**
 * Copy of the `Order` model the other scenarios generate from `test-api.yaml`, so the JSON-B tests read and write
 * the same document as the Jackson tests.
 */
data class Order @JsonbCreator constructor(
    @param:JsonbProperty("id") val id: UUID,
    @param:JsonbProperty("isbn") val isbn: Isbn,
    @param:JsonbProperty("quantity") val quantity: Quantity,
    @param:JsonbProperty("email") val email: EMail,
    @param:JsonbProperty("currency") val currency: String,
    @param:JsonbProperty("orderDate") val orderDate: OrderDate,
    @param:JsonbProperty("couponCode") val couponCode: CouponCode? = null
)
