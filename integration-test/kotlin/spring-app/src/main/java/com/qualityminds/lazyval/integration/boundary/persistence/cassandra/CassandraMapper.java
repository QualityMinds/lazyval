package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface CassandraMapper {

    Order toDomain(CassandraOrder order);
    List<Order> toDomain(List<CassandraOrder> orders);

    CassandraOrder toDB(Order order);
}
