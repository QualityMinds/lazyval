package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
interface RedisMapper {

    Order toDomain(RedisOrder order);
    List<Order> toDomain(Iterable<RedisOrder> orders);

    RedisOrder toDB(Order order);
}
