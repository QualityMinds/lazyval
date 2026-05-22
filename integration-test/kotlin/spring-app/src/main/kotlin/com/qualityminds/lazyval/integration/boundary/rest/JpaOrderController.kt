package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.api.OrderJpaApi
import com.qualityminds.lazyval.integration.boundary.rest.model.CreateOrder
import com.qualityminds.lazyval.integration.boundary.rest.model.Order
import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/order/jpa", produces = [MediaType.APPLICATION_JSON_VALUE])
class JpaOrderController(
    private val mapper: RestMapper,
    @Qualifier("jpa") private val repository: OrderRepository
) : OrderJpaApi {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    override fun createOrderJpa(@RequestBody createOrder: CreateOrder): ResponseEntity<Order> {
        val newOrder = com.qualityminds.lazyval.integration.domain.Order.create(
            Isbn.parse(createOrder.isbn),
            Quantity(createOrder.quantity),
            EMail(createOrder.email),
        )
        repository.save(newOrder)
        return ResponseEntity.ok(mapper.toDto(newOrder))
    }

    @GetMapping("/isbn/{isbn}")
    override fun findOrdersByIsbnJpa(@PathVariable isbn: String): ResponseEntity<List<Order>> {
        return ResponseEntity.ok(mapper.toDto(repository.findByIsbn(Isbn.parse(isbn))))
    }

    @GetMapping
    override fun getAllOrdersJpa(): ResponseEntity<List<Order>> {
        return ResponseEntity.ok(mapper.toDto(repository.findAll()))
    }

    @GetMapping("/{id}")
    override fun getOrderByIdJpa(@PathVariable id: UUID): ResponseEntity<Order> {
        return ResponseEntity.ok(mapper.toDto(repository.getById(id)))
    }
}
