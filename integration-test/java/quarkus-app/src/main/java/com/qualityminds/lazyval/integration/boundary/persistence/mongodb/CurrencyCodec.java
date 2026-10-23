package com.qualityminds.lazyval.integration.boundary.persistence.mongodb;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

import java.util.Currency;

/**
 * Stores {@link Currency}, a value object Lazyval knows nothing about, as its ISO code. Without it the
 * driver falls back to its POJO codec: writing silently succeeds with Currency's internals, and reading
 * fails because Currency has no public constructor. Registered through {@code lazyval.mongodb.codecs},
 * which adds it to the generated codecs.
 */
class CurrencyCodec implements Codec<Currency> {

    CurrencyCodec() {
    }

    @Override
    public void encode(BsonWriter writer, Currency value, EncoderContext encoderContext) {
        writer.writeString(value.getCurrencyCode());
    }

    @Override
    public Currency decode(BsonReader reader, DecoderContext decoderContext) {
        return Currency.getInstance(reader.readString());
    }

    @Override
    public Class<Currency> getEncoderClass() {
        return Currency.class;
    }
}
