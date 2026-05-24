package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.ZoneOffset;
import java.util.Date;

@ReadingConverter
public class DateToOrderDateConverter implements Converter<Date, OrderDate> {

    public DateToOrderDateConverter() {
    }

    @Override
    public OrderDate convert(Date source) {
        return new OrderDate(source.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
    }
}
