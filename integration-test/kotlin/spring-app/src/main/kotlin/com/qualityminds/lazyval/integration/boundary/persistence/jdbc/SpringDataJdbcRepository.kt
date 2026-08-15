package com.qualityminds.lazyval.integration.boundary.persistence.jdbc

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Spring Data JDBC has no store-specific repository interface to extend, so this is a plain
 * `CrudRepository`. Detection works off the relational `@Table` on [JdbcOrder].
 */
@Repository
interface SpringDataJdbcRepository : CrudRepository<JdbcOrder, UUID> {

    @Query("SELECT * FROM JDBC_ORDERS WHERE isbn = :isbn")
    fun findByIsbn(isbn: String): List<JdbcOrder>
}
