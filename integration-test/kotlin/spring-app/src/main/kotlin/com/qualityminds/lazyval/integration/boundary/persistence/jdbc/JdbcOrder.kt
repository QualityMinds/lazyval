package com.qualityminds.lazyval.integration.boundary.persistence.jdbc

import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.CouponCode
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.util.*

/**
 * Its own table rather than the JPA one: both stores live in this application, and sharing `orders`
 * would mean two mapping layers and two seeded copies fighting over the same rows.
 *
 * The relational [Table] annotation is also what keeps repository detection unambiguous. With JPA,
 * Cassandra, MongoDB and JDBC on the classpath, Spring Data runs in strict mode and assigns each
 * repository by the identifying annotation on its domain type — `@Entity` for JPA, relational
 * `@Table` for JDBC.
 *
 * Upper case on purpose: H2 folds unquoted DDL identifiers to upper case, while Spring Data takes an
 * explicitly given `@Table` name literally and quotes it. Derived column names go through
 * IdentifierProcessing and are upper-cased already, so only the table name needs this.
 *
 * [Persistable] is implemented because the domain assigns its own UUIDs. Spring Data Relational
 * decides insert-vs-update from whether the id is `null`, so a pre-populated id would make `save()`
 * issue an UPDATE that matches no rows and silently does nothing.
 */
@Table("JDBC_ORDERS")
data class JdbcOrder(
    @Id
    private val id: UUID,
    val isbn: Isbn,
    val quantity: Quantity,
    val email: EMail,
    val orderDate: OrderDate,
    val couponCode: CouponCode? = null,
) : Persistable<UUID> {

    @Transient
    private var newEntity: Boolean = false

    // public, not internal: JdbcMapper is a MapStruct interface and therefore Java, and Kotlin
    // mangles the JVM names of internal members so Java callers cannot see them
    fun markNew() {
        newEntity = true
    }

    override fun getId(): UUID = id

    @Transient
    override fun isNew(): Boolean = newEntity
}
