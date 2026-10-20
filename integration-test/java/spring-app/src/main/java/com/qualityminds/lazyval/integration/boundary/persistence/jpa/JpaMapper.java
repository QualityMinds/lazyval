package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
interface JpaMapper {

    Order toDomain(JpaOrder order);

    List<Order> toDomain(List<JpaOrder> orders);

    JpaOrder toDB(Order order);
}
