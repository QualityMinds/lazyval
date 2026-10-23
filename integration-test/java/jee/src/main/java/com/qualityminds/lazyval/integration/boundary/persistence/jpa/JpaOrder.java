package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.CouponCode;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class JpaOrder {
    @Id
    public UUID id;
    public Isbn isbn;
    public Quantity quantity;
    public EMail email;
    public Currency currency;
    public OrderDate orderDate;
    public CouponCode couponCode;

    public JpaOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, Currency currency,
                    @Nullable CouponCode couponCode) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.currency = currency;
        this.orderDate = OrderDate.now();
        this.couponCode = couponCode;
    }

    protected JpaOrder() {}
}
