package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.shared.Isbn
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SpringDataRepository : CrudRepository<JpaOrder, UUID> {

    @Query("SELECT o FROM JpaOrder o WHERE o.isbn = :isbn")
    fun findByIsbn(isbn: Isbn): List<JpaOrder>
}