package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import java.nio.ByteBuffer
import java.util.*

/**
 * Writes an order the way Spring Data Redis lays out a repository entity: a hash `orders:<id>` with one
 * field per property, the set `orders` of all ids, and `orders:isbn:<isbn>` as the secondary index for
 * [findByIsbn].
 *
 * Lettuce has one codec per connection, so the hash goes through a `ByteArray` connection and every
 * domain-primitive field is encoded with its generated codec. [Currency] is no domain-primitive, so the
 * application encodes it itself.
 */
@ApplicationScoped
@Named("redis")
internal class RedisOrderRepository @Inject constructor(client: RedisClient) : OrderRepository {

    private val hashes: StatefulRedisConnection<String, ByteArray> =
        client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))
    private val index: StatefulRedisConnection<String, String> = client.connect(StringCodec.UTF8)

    @PreDestroy
    fun close() {
        hashes.close()
        index.close()
    }

    override fun save(order: Order) {
        val fields = linkedMapOf(
            "isbn" to LazyvalLettuceCodecs.ISBN.encodeValue(order.isbn).toBytes(),
            "quantity" to LazyvalLettuceCodecs.QUANTITY.encodeValue(order.quantity).toBytes(),
            "email" to LazyvalLettuceCodecs.E_MAIL.encodeValue(order.email).toBytes(),
            "currency" to order.currency.currencyCode.encodeToByteArray(),
            "orderDate" to LazyvalLettuceCodecs.ORDER_DATE.encodeValue(order.orderDate).toBytes(),
        )
        order.couponCode?.let { fields["couponCode"] = LazyvalLettuceCodecs.COUPON_CODE.encodeValue(it).toBytes() }
        val id = order.id.toString()
        hashes.sync().hset(orderKey(id), fields)
        index.sync().sadd(ALL, id)
        index.sync().sadd(isbnKey(order.isbn), id)
    }

    override fun findAll(): List<Order> = load(index.sync().smembers(ALL)).sortedBy { it.isbn.value }

    override fun getById(id: UUID): Order? = read(id.toString())

    override fun findByIsbn(isbn: Isbn): List<Order> = load(index.sync().smembers(isbnKey(isbn)))

    private fun load(ids: Set<String>): List<Order> = ids.mapNotNull { read(it) }

    private fun read(id: String): Order? {
        val fields = hashes.sync().hgetall(orderKey(id))
        if (fields.isEmpty()) return null
        return Order(
            id = UUID.fromString(id),
            isbn = LazyvalLettuceCodecs.ISBN.decodeValue(fields.getValue("isbn").buffer()),
            quantity = LazyvalLettuceCodecs.QUANTITY.decodeValue(fields.getValue("quantity").buffer()),
            email = LazyvalLettuceCodecs.E_MAIL.decodeValue(fields.getValue("email").buffer()),
            currency = Currency.getInstance(fields.getValue("currency").decodeToString()),
            orderDate = LazyvalLettuceCodecs.ORDER_DATE.decodeValue(fields.getValue("orderDate").buffer()),
            couponCode = fields["couponCode"]?.let { LazyvalLettuceCodecs.COUPON_CODE.decodeValue(it.buffer()) },
        )
    }

    private companion object {
        const val ALL = "orders"

        fun orderKey(id: String) = "orders:$id"

        fun isbnKey(isbn: Isbn) = "orders:isbn:${isbn.value}"

        fun ByteBuffer.toBytes(): ByteArray = ByteArray(remaining()).also { get(it) }

        fun ByteArray.buffer(): ByteBuffer = ByteBuffer.wrap(this)
    }
}
