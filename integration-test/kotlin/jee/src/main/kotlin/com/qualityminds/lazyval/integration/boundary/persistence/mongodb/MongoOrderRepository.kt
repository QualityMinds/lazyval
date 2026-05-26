package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters.eq
import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.util.UUID

@ApplicationScoped
@Named("mongo")
class MongoOrderRepository @Inject constructor(
    private val client: MongoClient,
    private val mapper: MongoMapper
) : OrderRepository {

    private fun orders(): MongoCollection<MongoOrder> =
        client.getDatabase("jee").getCollection("orders", MongoOrder::class.java)

    override fun save(order: Order) {
        val doc = mapper.toDB(order)
        // ClientSession + withTransaction gives true Mongo transactional semantics on writes.
        // Note: requires the Mongo deployment to be a replica set (see AbstractLibertyIT).
        client.startSession().use { session ->
            session.withTransaction {
                orders().insertOne(session, doc)
            }
        }
    }

    override fun findAll(): List<Order> =
        orders().find()
            .toList()
            .map(mapper::toDomain)
            .sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? =
        orders().find(eq("_id", id)).first()?.let(mapper::toDomain)

    override fun findByIsbn(isbn: Isbn): List<Order> =
        // codec-aware filter: the registered IsbnCodec encodes the value object to a plain String,
        // so Filters.eq picks up the same representation that was stored.
        orders().find(eq("isbn", isbn))
            .toList()
            .map(mapper::toDomain)
            .sortedBy { it.isbn.value }
}
