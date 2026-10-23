package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.quarkus.redis.datasource.RedisDataSource
import io.smallrye.common.annotation.Identifier
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * The same key layout Spring Data Redis uses for its repositories, built by hand: one value per order
 * under `orders:<id>`, the set `orders` of all ids, and `orders:isbn:<isbn>` as the secondary index for
 * [findByIsbn].
 */
@ApplicationScoped
@Identifier("redis")
internal class RedisOrderRepository @Inject constructor(
    dataSource: RedisDataSource,
    private val mapper: RedisMapper
) : OrderRepository {

    private val orders = dataSource.value(RedisOrder::class.java)
    private val ids = dataSource.set(UUID::class.java)

    override fun save(order: Order) {
        val entity = mapper.toDB(order)
        orders.set(orderKey(entity.id), entity)
        ids.sadd(ALL, entity.id)
        ids.sadd(isbnKey(entity.isbn), entity.id)
    }

    override fun findAll(): List<Order> = load(ids.smembers(ALL)).sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? = orders.get(orderKey(id))?.let { mapper.toDomain(it) }

    override fun findByIsbn(isbn: Isbn): List<Order> = load(ids.smembers(isbnKey(isbn)))

    private fun load(orderIds: Set<UUID>): List<Order> {
        if (orderIds.isEmpty()) return emptyList()
        val keys = orderIds.map { orderKey(it) }.toTypedArray()
        return mapper.toDomain(orders.mget(*keys).values.filterNotNull())
    }

    private companion object {
        const val ALL = "orders"

        fun orderKey(id: UUID) = "orders:$id"

        fun isbnKey(isbn: Isbn) = "orders:isbn:${isbn.value}"
    }
}
