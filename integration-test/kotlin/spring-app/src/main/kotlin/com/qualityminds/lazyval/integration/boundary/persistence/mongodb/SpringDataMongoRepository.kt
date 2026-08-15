package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SpringDataMongoRepository : MongoRepository<MongoOrder, UUID> {
    @Query("{ 'isbn' : ?0 }")
    fun findByIsbn(isbn: Isbn): MutableList<MongoOrder>?
}