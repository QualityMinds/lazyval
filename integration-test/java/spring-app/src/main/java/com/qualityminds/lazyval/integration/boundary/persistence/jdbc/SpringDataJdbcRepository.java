package com.qualityminds.lazyval.integration.boundary.persistence.jdbc;

import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JDBC has no store-specific repository interface to extend, so this is a plain
 * {@code CrudRepository}. Detection works off the relational {@code @Table} on {@link JdbcOrder} —
 * see the class comment there for why that matters with four Spring Data modules on the classpath.
 */
@Repository
public interface SpringDataJdbcRepository extends CrudRepository<JdbcOrder, UUID> {

    @Query("SELECT * FROM JDBC_ORDERS WHERE isbn = :isbn")
    List<JdbcOrder> findByIsbn(Isbn isbn);
}
