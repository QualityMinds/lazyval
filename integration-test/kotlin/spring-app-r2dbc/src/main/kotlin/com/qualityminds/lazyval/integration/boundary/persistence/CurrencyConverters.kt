package com.qualityminds.lazyval.integration.boundary.persistence

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.util.*

// java.util.Currency is a value object Lazyval knows nothing about, and Spring Data R2DBC has no
// converter for it either: without these, the mapping context tries to introspect it as an entity and
// the application fails to start. Registered through lazyval.springdata.r2dbc.converters, since
// Lazyval owns the R2dbcCustomConversions bean.

@WritingConverter
internal class CurrencyToStringConverter : Converter<Currency, String> {
    override fun convert(source: Currency): String = source.currencyCode
}

@ReadingConverter
internal class StringToCurrencyConverter : Converter<String, Currency> {
    override fun convert(source: String): Currency = Currency.getInstance(source)
}
