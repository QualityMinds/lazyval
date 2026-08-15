package com.qualityminds.lazyval.integration.boundary.persistence.jdbc

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Qualifier("jdbc")
class JdbcOrderRepository(
    private val springDataRepository: SpringDataJdbcRepository,
    private val mapper: JdbcMapper
) : OrderRepository {

    override fun save(order: Order) {
        springDataRepository.save(mapper.toDB(order))
    }

    override fun findAll(): List<Order> =
        mapper.toDomain(springDataRepository.findAll().toList()).sortedBy { it?.isbn?.value }

    override fun getById(id: UUID): Order? =
        springDataRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)

    override fun findByIsbn(isbn: Isbn): List<Order> =
        mapper.toDomain(springDataRepository.findByIsbn(isbn))
}
