package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.bson.codecs.pojo.annotations.BsonCreator
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonProperty
import java.util.UUID

// Kotlin compilation runs without -java-parameters, so the POJO codec cannot recover
// constructor parameter names by reflection — every @BsonCreator parameter needs an
// explicit @BsonProperty (mirrored on the getter so serialization picks the same name).
data class MongoOrder @BsonCreator constructor(
    @param:BsonId @get:BsonId
    val id: UUID,
    @param:BsonProperty("isbn")
    val isbn: Isbn,
    @param:BsonProperty("quantity")
    val quantity: Quantity,
    @param:BsonProperty("email")
    val email: EMail,
    @param:BsonProperty("orderdate") @get:BsonProperty("orderdate")
    val orderDate: OrderDate,
    @param:BsonProperty("couponcode") @get:BsonProperty("couponcode")
    val couponCode: CouponCode? = null
)
