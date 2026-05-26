package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class MongoOrder {
    @BsonId
    private UUID id;
    private Isbn isbn;
    private Quantity quantity;
    private EMail email;
    @BsonProperty("orderdate")
    private OrderDate orderDate;
    @Nullable
    @BsonProperty("couponcode")
    private CouponCode couponCode;

    @BsonCreator
    public MongoOrder(
            @BsonId UUID id,
            @BsonProperty("isbn") Isbn isbn,
            @BsonProperty("quantity") Quantity quantity,
            @BsonProperty("email") EMail email,
            @BsonProperty("orderdate") OrderDate orderDate,
            @BsonProperty("couponcode") @Nullable CouponCode couponCode) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.orderDate = orderDate;
        this.couponCode = couponCode;
    }

    public UUID getId() {
        return id;
    }

    public Isbn getIsbn() {
        return isbn;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public EMail getEmail() {
        return email;
    }

    public OrderDate getOrderDate() {
        return orderDate;
    }

    public @Nullable CouponCode getCouponCode() {
        return couponCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MongoOrder that = (MongoOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
