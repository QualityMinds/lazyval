package com.qualityminds.lazyval.integration.boundary.persistence;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

import java.util.Currency;

/*
 * java.util.Currency is a value object Lazyval knows nothing about, and Spring Data R2DBC has no
 * converter for it either: without these, the mapping context tries to introspect it as an entity and
 * the application fails to start. Registered through lazyval.springdata.r2dbc.converters, since
 * Lazyval owns the R2dbcCustomConversions bean.
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
