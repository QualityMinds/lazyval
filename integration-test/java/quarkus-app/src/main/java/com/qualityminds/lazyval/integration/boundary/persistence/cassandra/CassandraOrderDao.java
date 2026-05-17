package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Query;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import com.qualityminds.lazyval.integration.shared.Isbn;

import java.util.UUID;

@Dao
public interface CassandraOrderDao {
    @Update
    void update(CassandraOrder order);

    @Select
    PagingIterable<CassandraOrder> findAll();

    @Select
    CassandraOrder getById(UUID id);

    @Query("SELECT * FROM ${qualifiedTableId} WHERE isbn = :isbn ALLOW FILTERING")
    PagingIterable<CassandraOrder> findByIsbn(Isbn isbn);
}
