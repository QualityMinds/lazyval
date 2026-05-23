package com.qualityminds.lazyval.integration.domain;

import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record Order(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate, @Nullable CouponCode couponCode) {

    public static Order create(Isbn isbn, Quantity quantity, EMail email) {
        return new Order(UUID.randomUUID(), isbn, quantity, email, OrderDate.now(), null);
    }

    public static Order create(Isbn isbn, Quantity quantity, EMail email, @Nullable CouponCode couponCode) {
        return new Order(UUID.randomUUID(), isbn, quantity, email, OrderDate.now(), couponCode);
    }
}
