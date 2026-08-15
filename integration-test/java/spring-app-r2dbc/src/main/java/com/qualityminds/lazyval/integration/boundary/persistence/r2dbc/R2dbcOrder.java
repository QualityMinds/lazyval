package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * The value-typed properties (Isbn, Quantity, EMail, OrderDate, CouponCode) are written and read
 * through the converters that lazyval generates into {@code LazyvalSpringDataConfiguration}, so the
 * columns are plain scalars — see {@code schema.sql}. Column names come from Spring Data's default
 * naming strategy: {@code orderDate} maps to {@code order_date}.
 * <p>
 * {@link Persistable} is implemented because the domain assigns its own UUIDs. Spring Data
 * Relational decides insert-vs-update from whether the id is {@code null}, so a pre-populated id
 * would make {@code save()} issue an UPDATE that matches no rows and silently does nothing. The
 * transient flag is off by default, which is what reads need; {@link R2dbcMapper} turns it on for
 * entities mapped from the domain.
 */
@Table("orders")
public class R2dbcOrder implements Persistable<UUID> {

    @Id
    private final UUID id;
    private final Isbn isbn;
    private final Quantity quantity;
    private final EMail email;
    private final OrderDate orderDate;
    private final @Nullable CouponCode couponCode;

    @Transient
    private boolean newEntity;

    public R2dbcOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate,
                      @Nullable CouponCode couponCode) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.orderDate = orderDate;
        this.couponCode = couponCode;
    }

    void markNew() {
        this.newEntity = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
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
}
