package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.data.cassandra.core.mapping.Column
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import java.util.*

@Table("orders")
data class CassandraOrder(
    @PrimaryKey
    val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    @Column("orderdate")
    val orderDate: OrderDate = OrderDate.now(),
    val couponCode: CouponCode? = null
)