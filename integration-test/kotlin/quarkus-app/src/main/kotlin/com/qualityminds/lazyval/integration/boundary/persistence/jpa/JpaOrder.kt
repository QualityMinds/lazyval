package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "orders")
class JpaOrder(
    @Id
    var id: UUID,
    var isbn: Isbn,
    var quantity: Quantity,
    var email: EMail,
    var orderDate: OrderDate = OrderDate.now()
)
