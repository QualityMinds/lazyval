package com.qualityminds.lazyval.integration.boundary.persistence.jdbc;

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
 * The value-typed properties are written and read through the converters lazyval generates into
 * {@code jdbcCustomConversions()}, so every column is a plain scalar — see {@code schema.sql}.
 * <p>
 * Its own table rather than the JPA one: both stores live in this application, and sharing
 * {@code orders} would mean two mapping layers and two seeded copies fighting over the same rows.
 * <p>
 * The relational {@link Table} annotation is also what keeps repository detection unambiguous.
 * With JPA, Cassandra, MongoDB and JDBC all on the classpath, Spring Data runs in strict mode and
 * assigns each repository by the identifying annotation on its domain type — {@code @Entity} for
 * JPA, relational {@code @Table} for JDBC.
 * <p>
 * {@link Persistable} is implemented because the domain assigns its own UUIDs. Spring Data
 * Relational decides insert-vs-update from whether the id is {@code null}, so a pre-populated id
 * would make {@code save()} issue an UPDATE that matches no rows and silently does nothing.
 */
// Upper case on purpose: H2 folds unquoted DDL identifiers to upper case, while Spring Data takes
// an explicitly given @Table name literally and quotes it. A lowercase name here would emit
// "jdbc_orders" and miss the JDBC_ORDERS that schema.sql actually created. Derived column names go
// through IdentifierProcessing and are upper-cased already, so only the table name needs this.
@Table("JDBC_ORDERS")
public class JdbcOrder implements Persistable<UUID> {

    @Id
    private final UUID id;
    private final Isbn isbn;
    private final Quantity quantity;
    private final EMail email;
    private final OrderDate orderDate;
    private final @Nullable CouponCode couponCode;

    @Transient
    private boolean newEntity;

    public JdbcOrder(UUID id, Isbn isbn, Quantity quantity, EMail email, OrderDate orderDate,
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
