package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import com.datastax.oss.driver.api.mapper.annotations.PropertyStrategy;
import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Entity
@CqlName( "orders")
@PropertyStrategy(mutable = true)
public class CassandraOrder {
    @PartitionKey
    private UUID id;
    private Isbn isbn;
    private Quantity quantity;
    private EMail email;
    @CqlName("orderdate")
    private OrderDate orderDate;
    @CqlName("couponcode")
    @Nullable
    private CouponCode couponCode;

    protected CassandraOrder() {}

    public CassandraOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, @Nullable CouponCode couponCode) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.orderDate = OrderDate.now();
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
}
