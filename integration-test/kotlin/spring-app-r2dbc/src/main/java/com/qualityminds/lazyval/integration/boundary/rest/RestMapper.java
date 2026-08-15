package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.LazyvalMapper;
import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.Mapper;

@Mapper(uses = {LazyvalMapper.class})
public interface RestMapper {

    // no List overload: the reactive controller maps element-wise over a Flux
    com.qualityminds.lazyval.integration.boundary.rest.model.Order toDto(Order order);
}
