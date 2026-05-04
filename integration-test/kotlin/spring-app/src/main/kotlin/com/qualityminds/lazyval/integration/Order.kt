package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

@Table(name = "orders")
@Entity
class Order constructor(
    isbn: Isbn,
    quantity: Quantity,
    email: EMail
){
    @Id
    @GeneratedValue
    var id: Long? = null
    var isbn: Isbn = isbn
    var quantity: Quantity = quantity
    var email: EMail = email
    var orderDate: OrderDate = OrderDate(LocalDate.now())
}

interface OrderRepository : JpaRepository<Order, Long>