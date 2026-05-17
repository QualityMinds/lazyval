package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Qualifier("jpa")
internal class JpaOrderRepository(
    private val springDataRepository: SpringDataRepository,
    private val mapper: JpaMapper
) : OrderRepository {

    override fun save(order: Order) {
        springDataRepository.save(mapper.toDB(order))
    }

    override fun findAll(): List<Order> {
        return mapper.toDomain(springDataRepository.findAll().toList())
    }

    override fun getById(id: UUID): Order? {
        return mapper.toDomain(springDataRepository.findById(id).orElse(null))
    }

    override fun findByIsbn(isbn: Isbn): List<Order> {
        return mapper.toDomain(springDataRepository.findByIsbn(isbn))
    }
}
