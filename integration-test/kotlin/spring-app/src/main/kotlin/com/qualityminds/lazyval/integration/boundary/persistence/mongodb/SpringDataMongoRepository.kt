package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SpringDataMongoRepository : MongoRepository<MongoOrder, UUID> {
    @Query("{ 'isbn' : ?0 }")
    fun findByIsbn(isbn: String?): MutableList<MongoOrder?>?
}