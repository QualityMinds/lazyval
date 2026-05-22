package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import java.util.UUID

data class CassandraOrder(
    val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val orderDate: OrderDate = OrderDate.now()
)
