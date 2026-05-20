package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
@Identifier("jpa")
class JpaOrderRepository @Inject constructor(
    private val repository: PanacheRepository,
    private val mapper: JpaMapper
) : OrderRepository {

    override fun save(order: Order) {
        repository.persist(mapper.toDB(order))
    }

    override fun findAll(): List<Order> =
        mapper.toDomain(repository.findAll().list()).sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? =
        repository.findById(id)?.let { mapper.toDomain(it) }

    override fun findByIsbn(isbn: Isbn): List<Order> =
        mapper.toDomain(repository.findByIsbn(isbn))
}
