package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Qualifier("mongo")
class MongoOrderRepository(
    private val springDataRepository: SpringDataMongoRepository,
    private val mapper: MongoMapper
) : OrderRepository {

    override fun save(order: Order) {
        springDataRepository.save(mapper.toDB(order))
    }

    override fun findAll(): List<Order> {
        return mapper.toDomain(springDataRepository.findAll()).sortedBy { it?.isbn?.value }
    }

    override fun getById(id: UUID): Order? {
        return springDataRepository.findById(id).map { mapper.toDomain(it) }.orElse(null)
    }

    override fun findByIsbn(isbn: Isbn): List<Order> {
        return mapper.toDomain(springDataRepository.findByIsbn(isbn))
    }
}