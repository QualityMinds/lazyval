package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import java.util.*

@RedisHash("orders")
data class RedisOrder(
    @Id
    val id: UUID,
    @Indexed // needed for findByIsbn
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val currency: Currency,
    val orderDate: OrderDate,
    val couponCode: CouponCode? = null
)
