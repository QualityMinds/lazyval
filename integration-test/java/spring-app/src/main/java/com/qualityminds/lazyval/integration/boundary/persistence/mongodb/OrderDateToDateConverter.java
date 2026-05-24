package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.ZoneOffset;
import java.util.Date;

@WritingConverter
public class OrderDateToDateConverter implements Converter<OrderDate, Date> {

    public OrderDateToDateConverter() {
    }

    @Override
    public Date convert(OrderDate source) {
        return Date.from(source.value().atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
