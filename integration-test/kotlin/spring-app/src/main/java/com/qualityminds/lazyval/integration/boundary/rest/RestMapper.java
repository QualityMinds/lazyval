package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.LazyvalMapper;
import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(uses = { LazyvalMapper.class})
public interface RestMapper {
    List<com.qualityminds.lazyval.integration.boundary.rest.model.Order> toDto(List<Order> orders);
    com.qualityminds.lazyval.integration.boundary.rest.model.Order toDto(Order order);
}
