package com.qualityminds.lazyval.integration.boundary.persistence.jdbc;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper
public interface JdbcMapper {

    Order toDomain(JdbcOrder order);

    List<Order> toDomain(List<JdbcOrder> orders);

    // newEntity is not mapped: it has no setter and is not a constructor parameter, so MapStruct
    // does not see it as a writable target. markNew() below sets it.
    JdbcOrder toDB(Order order);

    /**
     * Anything mapped from the domain is being inserted, so flag it as new — see
     * {@link JdbcOrder} for why Spring Data cannot infer that from the id.
     */
    @AfterMapping
    default void markNew(@MappingTarget JdbcOrder target) {
        target.markNew();
    }
}
