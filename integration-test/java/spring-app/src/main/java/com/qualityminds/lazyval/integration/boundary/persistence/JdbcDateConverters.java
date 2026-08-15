package com.qualityminds.lazyval.integration.boundary.persistence;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.sql.Date;

/**
 * The read-side bridge Spring Data JDBC needs for {@link OrderDate}.
 * <p>
 * This is the relational twin of the MongoDB date gotcha in {@code CustomSpringDataConverters}: the
 * JDBC driver hands a {@link java.sql.Date} to the converter layer for a {@code DATE} column, and
 * Spring's {@code ConversionService} does not chain converters — so the generated
 * {@code Converter<LocalDate, OrderDate>} is never reached and the read fails with
 * {@code ConverterNotFoundException}. Bridging needs an application-level decision about how a
 * SQL date maps onto a local date, which the generator cannot make.
 * <p>
 * Only the read direction needs bridging: the generated {@code OrderDate -> LocalDate} write
 * converter produces a type the driver binds natively.
 * <p>
 * Registered through {@code -Alazyval.springdata.jdbc.converters}; see the spring-app pom.
 */
@ReadingConverter
class SqlDateToOrderDateConverter implements Converter<Date, OrderDate> {

    SqlDateToOrderDateConverter() {
    }

    @Override
    public OrderDate convert(Date source) {
        return new OrderDate(source.toLocalDate());
    }
}
