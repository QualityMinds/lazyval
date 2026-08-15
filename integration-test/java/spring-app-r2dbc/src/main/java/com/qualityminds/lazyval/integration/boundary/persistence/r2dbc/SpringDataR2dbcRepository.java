package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc;

import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Extends the store-specific {@code R2dbcRepository} rather than a plain {@code CrudRepository}, so
 * repository detection stays unambiguous if another Spring Data module ever joins the classpath.
 */
@Repository
public interface SpringDataR2dbcRepository extends R2dbcRepository<R2dbcOrder, UUID> {

    @Query("SELECT * FROM orders WHERE isbn = :isbn")
    Flux<R2dbcOrder> findByIsbn(Isbn isbn);
}
