package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.qualityminds.lazyval.integration.domain.EMail;
import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderDate;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Named("cassandra")
public class CassandraOrderRepository implements OrderRepository {

    private final CqlSession session;
    private final CassandraMapper mapper;
    private final PreparedStatement insertStmt;
    private final PreparedStatement selectAllStmt;
    private final PreparedStatement selectByIdStmt;
    private final PreparedStatement selectByIsbnStmt;

    @Inject
    public CassandraOrderRepository(CqlSession session, CassandraMapper mapper) {
        this.session = session;
        this.mapper = mapper;
        insertStmt = session.prepare(
                "INSERT INTO orders (id, isbn, quantity, email, orderdate) VALUES (?, ?, ?, ?, ?)");
        selectAllStmt = session.prepare("SELECT * FROM orders");
        selectByIdStmt = session.prepare("SELECT * FROM orders WHERE id = ?");
        selectByIsbnStmt = session.prepare(
                "SELECT * FROM orders WHERE isbn = ? ALLOW FILTERING");
    }


    @Override
    public void save(Order order) {
        CassandraOrder co = mapper.toDB(order);
        BoundStatementBuilder builder = insertStmt.boundStatementBuilder();
        builder = builder.set("id", co.getId(), UUID.class);
        builder = builder.set("isbn", co.getIsbn(), Isbn.class);
        builder = builder.set("quantity", co.getQuantity(), Quantity.class);
        builder = builder.set("email", co.getEmail(), EMail.class);
        builder = builder.set("orderdate", co.getOrderDate(), OrderDate.class);
        session.execute(builder.build());
    }

    @Override
    public List<Order> findAll() {
        return session.execute(selectAllStmt.bind())
                .all()
                .stream()
                .map(this::rowToEntity)
                .map(mapper::toDomain)
                .sorted(Comparator.comparing(order -> order.isbn().value())).toList();
    }

    @Override
    public Order getById(UUID id) {
        Row row = session.execute(selectByIdStmt.bind(id)).one();
        if (row == null) {
            return null;
        }
        return mapper.toDomain(rowToEntity(row));
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        BoundStatementBuilder builder = selectByIsbnStmt.boundStatementBuilder();
        builder = builder.set("isbn", isbn, Isbn.class);
        return session.execute(builder.build())
                .all()
                .stream()
                .map(this::rowToEntity)
                .map(mapper::toDomain)
                .sorted(Comparator.comparing(order -> order.isbn().value())).toList();
    }

    private CassandraOrder rowToEntity(Row row) {
        CassandraOrder order = new CassandraOrder(
                row.getUuid("id"),
                row.get("isbn", Isbn.class),
                row.get("quantity", Quantity.class),
                row.get("email", EMail.class)
        );
        order.setOrderDate(row.get("orderdate", OrderDate.class));
        return order;
    }
}
