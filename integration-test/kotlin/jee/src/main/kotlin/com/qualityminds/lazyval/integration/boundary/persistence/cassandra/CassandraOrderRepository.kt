package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.util.UUID

@ApplicationScoped
@Named("cassandra")
class CassandraOrderRepository @Inject constructor(
    private val session: CqlSession,
    private val mapper: CassandraMapper
) : OrderRepository {

    private val insertStmt: PreparedStatement = session.prepare(
        "INSERT INTO orders (id, isbn, quantity, email, orderdate) VALUES (?, ?, ?, ?, ?)"
    )
    private val selectAllStmt: PreparedStatement = session.prepare("SELECT * FROM orders")
    private val selectByIdStmt: PreparedStatement = session.prepare("SELECT * FROM orders WHERE id = ?")
    private val selectByIsbnStmt: PreparedStatement = session.prepare(
        "SELECT * FROM orders WHERE isbn = ? ALLOW FILTERING"
    )

    override fun save(order: Order) {
        val co = mapper.toDB(order)
        val statement = insertStmt.boundStatementBuilder()
            .set("id", co.id, UUID::class.java)
            .set("isbn", co.isbn, Isbn::class.java)
            .set("quantity", co.quantity, Quantity::class.java)
            .set("email", co.email, EMail::class.java)
            .set("orderdate", co.orderDate, OrderDate::class.java)
            .build()
        session.execute(statement)
    }

    override fun findAll(): List<Order> =
        session.execute(selectAllStmt.bind())
            .all()
            .map { mapper.toDomain(rowToEntity(it)) }
            .sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? {
        val row: Row = session.execute(selectByIdStmt.bind(id)).one() ?: return null
        return mapper.toDomain(rowToEntity(row))
    }

    override fun findByIsbn(isbn: Isbn): List<Order> {
        val statement = selectByIsbnStmt.boundStatementBuilder()
            .set("isbn", isbn, Isbn::class.java)
            .build()
        return session.execute(statement)
            .all()
            .map { mapper.toDomain(rowToEntity(it)) }
            .sortedBy { it.isbn.value }
    }

    private fun rowToEntity(row: Row): CassandraOrder = CassandraOrder(
        id = row.getUuid("id")!!,
        isbn = row.get("isbn", Isbn::class.java)!!,
        quantity = row.get("quantity", Quantity::class.java)!!,
        email = row.get("email", EMail::class.java)!!,
        orderDate = row.get("orderdate", OrderDate::class.java)!!
    )
}
