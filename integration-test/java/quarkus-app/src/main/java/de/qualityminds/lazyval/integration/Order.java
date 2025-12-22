package de.qualityminds.lazyval.integration;

import de.qualityminds.lazyval.integration.shared.Isbn;
import de.qualityminds.lazyval.integration.shared.Quantity;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order extends PanacheEntity {
    public Isbn isbn;
    // Quantity tests an boxed primitive from shared
    public Quantity quantity;
    public EMail email;

    public Order(Isbn isbn, Quantity quantity, EMail email) {
        this.isbn = isbn;
        this.quantity = quantity;
        this.email = email;
    }

    protected Order() {}
}
