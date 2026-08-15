package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc

import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.*

/**
 * Extends the store-specific `R2dbcRepository` rather than a plain `CrudRepository`, so repository
 * detection stays unambiguous if another Spring Data module ever joins the classpath.
 */
@Repository
interface SpringDataR2dbcRepository : R2dbcRepository<R2dbcOrder, UUID> {

    @Query("SELECT * FROM orders WHERE isbn = :isbn")
    fun findByIsbn(isbn: Isbn): Flux<R2dbcOrder>
}
