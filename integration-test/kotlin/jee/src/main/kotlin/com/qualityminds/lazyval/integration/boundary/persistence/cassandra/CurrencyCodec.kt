package com.qualityminds.lazyval.integration.boundary.persistence.cassandra

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType
import java.util.Currency

/**
 * Stores [Currency], a value object Lazyval knows nothing about, as its ISO code. The driver has no
 * codec for it, so without this binding or reading the `currency` column fails with a
 * `CodecNotFoundException`. Registered through `lazyval.cassandra.codecs`, which adds it to the
 * generated codecs.
 */
internal class CurrencyCodec : MappingCodec<String, Currency>(TypeCodecs.TEXT, GenericType.of(Currency::class.java)) {
    override fun innerToOuter(value: String?): Currency? = value?.let { Currency.getInstance(it) }
    override fun outerToInner(value: Currency?): String? = value?.currencyCode
}
