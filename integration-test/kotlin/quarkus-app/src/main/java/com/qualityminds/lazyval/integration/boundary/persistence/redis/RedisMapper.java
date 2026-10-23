package com.qualityminds.lazyval.integration.boundary.persistence.redis;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
interface RedisMapper {

    Order toDomain(RedisOrder order);

    List<Order> toDomain(Collection<RedisOrder> orders);

    RedisOrder toDB(Order order);
}
