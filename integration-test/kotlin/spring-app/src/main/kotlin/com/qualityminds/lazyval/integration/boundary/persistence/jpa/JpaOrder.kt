package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.*

@Table(name = "orders")
@Entity
class JpaOrder constructor(
    id: UUID,
    isbn: Isbn,
    quantity: Quantity,
    email: EMail
){
    @Id
    var id: UUID = id
    var isbn: Isbn = isbn
    var quantity: Quantity = quantity
    var email: EMail = email
    var orderDate: OrderDate = OrderDate(LocalDate.now())
}