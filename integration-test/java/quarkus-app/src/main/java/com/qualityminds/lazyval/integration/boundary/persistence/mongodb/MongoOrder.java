package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@MongoEntity(collection = "orders")
public class MongoOrder extends PanacheMongoEntity {
    private UUID id;
    private Isbn isbn;
    private Quantity quantity;
    private EMail email;
    @BsonProperty("orderdate")
    private OrderDate orderDate;
    @Nullable
    @BsonProperty("couponcode")
    private CouponCode couponCode;

    protected MongoOrder() {}

    public MongoOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate, @Nullable CouponCode couponCode) {
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

    public void setId(UUID id) {
        this.id = id;
    }

    public Isbn getIsbn() {
        return isbn;
    }

    public void setIsbn(Isbn isbn) {
        this.isbn = isbn;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public void setQuantity(Quantity quantity) {
        this.quantity = quantity;
    }

    public EMail getEmail() {
        return email;
    }

    public void setEmail(EMail email) {
        this.email = email;
    }

    public OrderDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(OrderDate orderDate) {
        this.orderDate = orderDate;
    }

    public @Nullable CouponCode getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(@Nullable CouponCode couponCode) {
        this.couponCode = couponCode;
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
