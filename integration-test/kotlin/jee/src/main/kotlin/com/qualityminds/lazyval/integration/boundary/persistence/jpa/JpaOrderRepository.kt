package com.qualityminds.lazyval.integration.boundary.persistence.jpa

import com.qualityminds.lazyval.integration.domain.Order
import com.qualityminds.lazyval.integration.domain.OrderRepository
import com.qualityminds.lazyval.integration.shared.Isbn
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@ApplicationScoped
@Named("jpa")
class JpaOrderRepository @Inject constructor(
    private val mapper: JpaMapper
) : OrderRepository {

    @field:PersistenceContext
    private lateinit var em: EntityManager

    override fun save(order: Order) {
        em.persist(mapper.toDB(order))
    }

    override fun findAll(): List<Order> {
        val jpaOrders = em.createQuery("SELECT o FROM JpaOrder o", JpaOrder::class.java)
            .resultStream
            .sorted(Comparator.comparing { it.isbn.value })
            .toList()
        return mapper.toDomain(jpaOrders)
    }

    override fun getById(id: UUID): Order? {
        val jpaOrder = em.find(JpaOrder::class.java, id) ?: return null
        return mapper.toDomain(jpaOrder)
    }

    override fun findByIsbn(isbn: Isbn): List<Order> {
        val jpaOrders = em.createQuery("SELECT o FROM JpaOrder o WHERE o.isbn = :isbn", JpaOrder::class.java)
            .setParameter("isbn", isbn)
            .resultStream
            .sorted(Comparator.comparing { it.isbn.value })
            .toList()
        return mapper.toDomain(jpaOrders)
    }
}
