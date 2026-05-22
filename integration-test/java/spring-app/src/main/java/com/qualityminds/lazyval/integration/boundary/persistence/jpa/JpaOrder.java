package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "orders")
public class JpaOrder {

    @Id
    public UUID id;
    public Isbn isbn;
    public Quantity quantity;
    public EMail email;
    public OrderDate orderDate;

    protected JpaOrder() {
    }

    public JpaOrder(UUID id, Isbn isbn, Quantity quantity, EMail email) {
        this.id = id;
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
        this.orderDate = OrderDate.now();
    }
}
