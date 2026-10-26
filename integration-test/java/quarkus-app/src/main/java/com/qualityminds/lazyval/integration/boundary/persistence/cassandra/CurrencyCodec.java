package com.qualityminds.lazyval.integration.boundary.persistence.cassandra;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import org.jspecify.annotations.Nullable;

import java.util.Currency;

/**
 * Stores {@link Currency}, a value object Lazyval knows nothing about, as its ISO code. The driver has
 * no codec for it, so without this the object mapper rejects {@code CassandraOrder} at startup.
 * Registered through {@code lazyval.cassandra.codecs}, which adds it to the generated codecs.
 */
class CurrencyCodec extends MappingCodec<String, Currency> {

    CurrencyCodec() {
        super(TypeCodecs.TEXT, GenericType.of(Currency.class));
    }

    @Override
    protected @Nullable Currency innerToOuter(@Nullable String value) {
        return value == null ? null : Currency.getInstance(value);
    }

    @Override
    protected @Nullable String outerToInner(@Nullable Currency value) {
        return value == null ? null : value.getCurrencyCode();
    }
}
