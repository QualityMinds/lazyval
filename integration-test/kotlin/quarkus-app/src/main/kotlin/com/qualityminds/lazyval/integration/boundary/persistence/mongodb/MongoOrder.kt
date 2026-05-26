package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import io.quarkus.mongodb.panache.common.MongoEntity
import io.quarkus.mongodb.panache.kotlin.PanacheMongoCompanionBase
import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntityBase
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import java.util.UUID

@MongoEntity(collection = "orders")
class MongoOrder(
    @BsonId
    var id: UUID,
    var isbn: Isbn,
    var quantity: Quantity,
    var email: EMail,
    @field:BsonProperty("orderdate")
    var orderDate: OrderDate,
    @field:BsonProperty("couponcode")
    var couponCode: CouponCode? = null
) : PanacheMongoEntityBase() {

    companion object : PanacheMongoCompanionBase<MongoOrder, UUID>

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MongoOrder) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
