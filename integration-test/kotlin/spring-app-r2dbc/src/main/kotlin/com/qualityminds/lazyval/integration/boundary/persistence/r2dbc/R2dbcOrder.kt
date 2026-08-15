package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.util.*

/**
 * The value-typed properties are written and read through the converters KSP generates into
 * `LazyvalSpringDataConfiguration`, so every column is a plain scalar — see `schema.sql`. Column
 * names come from Spring Data's default naming strategy, so `orderDate` maps to `order_date`.
 *
 * [Persistable] is implemented because the domain assigns its own UUIDs. Spring Data Relational
 * decides insert-vs-update from whether the id is `null`, so a pre-populated id would make `save()`
 * issue an UPDATE that matches no rows and silently does nothing. The transient flag is off by
 * default, which is what reads need; [R2dbcMapper] turns it on for entities mapped from the domain.
 */
@Table("orders")
data class R2dbcOrder(
    @Id
    private val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val orderDate: OrderDate,
    val couponCode: CouponCode? = null,
) : Persistable<UUID> {

    @Transient
    private var newEntity: Boolean = false

    // public, not internal: R2dbcMapper is a MapStruct interface and therefore Java, and Kotlin
    // mangles the JVM names of internal members so Java callers cannot see them
    fun markNew() {
        newEntity = true
    }

    override fun getId(): UUID = id

    @Transient
    override fun isNew(): Boolean = newEntity
}
