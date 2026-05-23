package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Table("orders")
public class CassandraOrder {

    @PrimaryKey
    private UUID id;
    private Isbn isbn;
    private Quantity quantity;
    private EMail email;
    @Column("orderdate")
    private OrderDate orderDate;
    @Nullable
    private CouponCode couponCode;

    protected CassandraOrder() {
    }

    public CassandraOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.orderDate = orderDate == null ? OrderDate.now() : orderDate;
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

    @Nullable
    public CouponCode getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(@Nullable CouponCode couponCode) {
        this.couponCode = couponCode;
    }
}
