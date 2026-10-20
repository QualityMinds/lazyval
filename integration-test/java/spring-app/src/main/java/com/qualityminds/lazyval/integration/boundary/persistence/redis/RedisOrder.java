package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.util.UUID;

@RedisHash("orders")
record RedisOrder(
        @Id UUID id,
        @Indexed Isbn isbn,     // needed for findByIsbn
        Quantity quantity, EMail email, OrderDate orderDate,
        @Nullable CouponCode couponCode
) {}
