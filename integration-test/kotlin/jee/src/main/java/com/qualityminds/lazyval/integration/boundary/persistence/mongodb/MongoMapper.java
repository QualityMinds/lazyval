package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface MongoMapper {

    Order toDomain(MongoOrder order);
    List<Order> toDomain(List<MongoOrder> orders);

    MongoOrder toDB(Order order);

}
