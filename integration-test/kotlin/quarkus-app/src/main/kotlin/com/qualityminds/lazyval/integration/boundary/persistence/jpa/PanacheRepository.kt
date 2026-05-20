package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.shared.Isbn
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PanacheRepository : PanacheRepository<JpaOrder> {

    fun findById(id: UUID): JpaOrder? = find("id", id).firstResult()

    fun findByIsbn(isbn: Isbn): List<JpaOrder> = find("isbn", isbn).list()
}
