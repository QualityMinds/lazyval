package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.shared.Isbn;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataRedisRepository extends CrudRepository<RedisOrder, UUID> {

    List<RedisOrder> findByIsbn(Isbn isbn);

}
