package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = { LazyvalMapper.class})
public interface TestMapper {

    com.qualityminds.lazyval.integration.client.model.Order toClientOrder(Order domain);

    List<com.qualityminds.lazyval.integration.client.model.Order> toClientOrder(List<Order> domain);

    Order toDomainOrder(com.qualityminds.lazyval.integration.client.model.Order domain);

    List<Order> toDomainOrder(List<com.qualityminds.lazyval.integration.client.model.Order> domain);

}