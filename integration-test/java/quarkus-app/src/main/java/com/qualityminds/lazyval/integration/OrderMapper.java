package com.qualityminds.lazyval.integration;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi", uses = { LazyvalMapper.class })
public interface OrderMapper {


    List<OrderDto> toDto(List<Order> orders);
    OrderDto toDto(Order order);

    @Mapping(target = "id", ignore = true)
    Order toEntity(CreateOrderDto dto);
}
