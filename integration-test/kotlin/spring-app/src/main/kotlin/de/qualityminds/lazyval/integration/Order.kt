package de.qualityminds.lazyval.integration

import de.qualityminds.lazyval.integration.shared.Isbn
import de.qualityminds.lazyval.integration.shared.Quantity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Table(name = "orders")
@Entity
class Order (
    var isbn: Isbn,
    var quantity: Quantity,
    var email: EMail,
){
    @Id
    @GeneratedValue
    var id: Long? = null
}

interface OrderRepository : JpaRepository<Order, Long>