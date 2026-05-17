package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import org.springframework.data.cassandra.repository.CassandraRepository
import org.springframework.data.cassandra.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SpringDataCassandraRepository : CassandraRepository<CassandraOrder, UUID> {

    @Query("SELECT * FROM orders WHERE isbn = ?0 ALLOW FILTERING")
    fun findByIsbn(isbn: String): List<CassandraOrder>
}
