package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
@Identifier("cassandra")
class CassandraOrderRepository @Inject constructor(
    private val dao: CassandraOrderDao,
    private val session: QuarkusCqlSession,
    private val mapper: CassandraMapper
) : OrderRepository {

    override fun save(order: Order) {
        dao.update(mapper.toDB(order))
    }

    override fun findAll(): List<Order> =
        mapper.toDomain(dao.findAll().all()).sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? =
        dao.getById(id)?.let { mapper.toDomain(it) }

    override fun findByIsbn(isbn: Isbn): List<Order> =
        mapper.toDomain(dao.findByIsbn(isbn).all()).sortedBy { it.isbn.value }
}
