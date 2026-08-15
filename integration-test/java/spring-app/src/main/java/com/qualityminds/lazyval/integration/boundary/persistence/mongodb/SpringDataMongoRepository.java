package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataMongoRepository extends MongoRepository<MongoOrder, UUID> {

    @Query("{ 'isbn' : ?0 }")
    List<MongoOrder> findByIsbn(Isbn isbn);
}
