package com.qualityminds.lazyval.integration

import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/order")
class OrderController(
    private val mapper: OrderMapper,
    private val repository: OrderRepository
) {

    @GetMapping
    fun getAllOrders(): List<OrderDto> {
        val orders = repository.findAll()
        return mapper.toDto(orders)
    }

    @Transactional
    @PostMapping
    fun addOrder(@RequestBody dto: CreateOrderDto): OrderDto {
        val order = mapper.toEntity(dto)
        order.orderDate = OrderDate(LocalDate.now())
        repository.save(order)
        return mapper.toDto(order)
    }
}
