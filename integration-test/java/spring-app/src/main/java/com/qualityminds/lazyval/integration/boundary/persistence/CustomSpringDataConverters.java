package com.qualityminds.lazyval.integration.boundary.persistence;

import com.qualityminds.lazyval.integration.domain.OrderDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Currency;
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

/*
 * java.util.Currency is a value object Lazyval knows nothing about, and the Spring Data stores have no
 * converter for it either: without these, the mapping context tries to introspect it as an entity and
 * the application fails to start. Registered through the lazyval.springdata.<store>.converters
 * options, since Lazyval owns the stores' CustomConversions beans.
 */
@WritingConverter
class CurrencyToStringConverter implements Converter<Currency, String> {

    CurrencyToStringConverter() {
    }

    @Override
    public String convert(Currency source) {
        return source.getCurrencyCode();
    }
}

@ReadingConverter
class StringToCurrencyConverter implements Converter<String, Currency> {

    StringToCurrencyConverter() {
    }

    @Override
    public Currency convert(String source) {
        return Currency.getInstance(source);
    }
}

/*
 * Redis stores bytes only and does not chain Currency -> String -> byte[], so the String pair above is
 * not enough there: the application starts, but saving fails with "Cannot convert value ... to bytes".
 */
@WritingConverter
class CurrencyToBytesConverter implements Converter<Currency, byte[]> {

    CurrencyToBytesConverter() {
    }

    @Override
    public byte[] convert(Currency source) {
        return source.getCurrencyCode().getBytes(StandardCharsets.UTF_8);
    }
}

@ReadingConverter
class BytesToCurrencyConverter implements Converter<byte[], Currency> {

    BytesToCurrencyConverter() {
    }

    @Override
    public Currency convert(byte[] source) {
        return Currency.getInstance(new String(source, StandardCharsets.UTF_8));
    }
}
