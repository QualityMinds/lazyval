package com.qualityminds.lazyval.integration.boundary.persistence.mongodb

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import java.util.Currency

/**
 * Stores [Currency], a value object Lazyval knows nothing about, as its ISO code. Without it the driver
 * falls back to its POJO codec: writing silently succeeds with Currency's internals, and reading fails
 * because Currency has no public constructor. Registered through `lazyval.mongodb.codecs`, which adds it
 * to the generated codecs.
 */
internal class CurrencyCodec : Codec<Currency> {
    override fun encode(writer: BsonWriter, value: Currency, encoderContext: EncoderContext) {
        writer.writeString(value.currencyCode)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Currency =
        Currency.getInstance(reader.readString())

    override fun getEncoderClass(): Class<Currency> = Currency::class.java
}
