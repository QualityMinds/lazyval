package com.qualityminds.lazyval.integration.boundary.persistence.r2dbc;

import com.qualityminds.lazyval.integration.domain.Order;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface R2dbcMapper {

    Order toDomain(R2dbcOrder order);

    // newEntity is not mapped: it has no setter and is not a constructor parameter, so MapStruct
    // does not see it as a writable target. markNew() below sets it.
    R2dbcOrder toDB(Order order);

    /**
     * Anything mapped from the domain is being inserted, so flag it as new — see
     * {@link R2dbcOrder} for why Spring Data cannot infer that from the id.
     */
    @AfterMapping
    default void markNew(@MappingTarget R2dbcOrder target) {
        target.markNew();
    }
}
