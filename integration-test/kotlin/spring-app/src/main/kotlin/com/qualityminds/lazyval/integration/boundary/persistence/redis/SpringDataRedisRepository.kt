package com.qualityminds.lazyval.integration.boundary.persistence.redis

import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.data.repository.CrudRepository
import java.util.*

interface SpringDataRedisRepository : CrudRepository<RedisOrder, UUID> {

    fun findByIsbn(isbn: Isbn): List<RedisOrder>
}
