package com.qualityminds.lazyval.integration.boundary.persistence

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.util.*

// java.util.Currency is a value object Lazyval knows nothing about, and the Spring Data stores have no
// converter for it either: without these, the mapping context tries to introspect it as an entity and
// the application fails to start. Registered through the lazyval.springdata.<store>.converters
// options, since Lazyval owns the stores' CustomConversions beans.

@WritingConverter
internal class CurrencyToStringConverter : Converter<Currency, String> {
    override fun convert(source: Currency): String = source.currencyCode
}

@ReadingConverter
internal class StringToCurrencyConverter : Converter<String, Currency> {
    override fun convert(source: String): Currency = Currency.getInstance(source)
}

// Redis stores bytes only and does not chain Currency -> String -> ByteArray, so the String pair above
// is not enough there: the application starts, but saving fails with "Cannot convert value ... to bytes".

@WritingConverter
internal class CurrencyToBytesConverter : Converter<Currency, ByteArray> {
    override fun convert(source: Currency): ByteArray = source.currencyCode.encodeToByteArray()
}

@ReadingConverter
internal class BytesToCurrencyConverter : Converter<ByteArray, Currency> {
    override fun convert(source: ByteArray): Currency = Currency.getInstance(source.decodeToString())
}
