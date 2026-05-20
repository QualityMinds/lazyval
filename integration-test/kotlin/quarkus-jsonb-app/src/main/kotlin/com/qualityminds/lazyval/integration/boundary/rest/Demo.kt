package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.domain.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.json.bind.annotation.JsonbCreator
import jakarta.json.bind.annotation.JsonbProperty

data class Demo @JsonbCreator constructor(
    @param:JsonbProperty("isbn") val isbn: Isbn,
    @param:JsonbProperty("quantity") val quantity: Quantity,
    @param:JsonbProperty("email") val email: EMail
)
