package com.qualityminds.lazyval.integration.boundary.persistence;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.time.ZoneOffset;
import java.util.Date;

@ReadingConverter
class DateToOrderDateConverter implements Converter<Date, OrderDate> {

    DateToOrderDateConverter() {
    }

    @Override
    public OrderDate convert(Date source) {
        return new OrderDate(source.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
    }
}

@WritingConverter
class OrderDateToDateConverter implements Converter<OrderDate, Date> {

    OrderDateToDateConverter() {
    }

    @Override
    public Date convert(OrderDate source) {
        return Date.from(source.value().atStartOfDay(ZoneOffset.UTC).toInstant());
    }
}
