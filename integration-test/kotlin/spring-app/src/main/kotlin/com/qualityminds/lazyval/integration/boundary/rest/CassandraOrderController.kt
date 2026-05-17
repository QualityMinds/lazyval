package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderCassandraApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/order/cassandra", produces = [MediaType.APPLICATION_JSON_VALUE])
class CassandraOrderController(
    private val mapper: RestMapper,
    @Qualifier("cassandra") private val repository: OrderRepository
) : OrderCassandraApi {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun createOrderCassandra(@RequestBody createOrder: CreateOrder): ResponseEntity<Order> {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            createOrder.isbn, createOrder.quantity, createOrder.email
        )
        repository.save(newOrder)
        return ResponseEntity.ok(mapper.toDto(newOrder))
    }

    @GetMapping("/isbn/{isbn}")
    override fun findOrdersByIsbnCassandra(@PathVariable isbn: String): ResponseEntity<List<Order>> {
        return ResponseEntity.ok(mapper.toDto(repository.findByIsbn(Isbn.parse(isbn))))
    }

    @GetMapping
    override fun getAllOrdersCassandra(): ResponseEntity<List<Order>> {
        return ResponseEntity.ok(mapper.toDto(repository.findAll()))
    }

    @GetMapping("/{id}")
    override fun getOrderByIdCassandra(@PathVariable id: UUID): ResponseEntity<Order> {
        return ResponseEntity.ok(mapper.toDto(repository.getById(id)))
    }
}
