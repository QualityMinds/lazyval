package com.qualityminds.lazyval.integation.evaluation

import com.qualityminds.lazyval.integration.boundary.persistence.mongodb.LazyvalMongoCodecs
import com.qualityminds.lazyval.integration.domain.OrderDate
import com.qualityminds.lazyval.integration.shared.EMail
import com.qualityminds.lazyval.integration.shared.Quantity
import org.bson.BSONException
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.BsonValue
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecRegistry
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Title

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Documents AI evaluation finding P1 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * {@code LazyvalMongoCodecs.asRegistry()} — the documented one-line setup, used by this module's {@code MongoConfig} —
 * puts {@code MongoClientSettings.getDefaultCodecRegistry()} in front of the generated provider, so the default
 * registry's record codec claims every Java-record domain-primitive before the generated codec is asked. A record
 * domain-primitive has to be stored as its scalar payload, like every other domain-primitive, and a sub-document must
 * not be decoded into it through the canonical constructor.
 * <p>
 * No database is involved: the registry encodes into and decodes from an in-memory {@link BsonDocument}.
 */
@Title("Evaluation P1 - Mongo asRegistry() and record domain-primitives")
class MongoRegistryEvaluationSpec extends Specification {

    @Shared
    CodecRegistry registry = LazyvalMongoCodecs.asRegistry()

    def "P1 asRegistry() encodes the record #value as its scalar payload"() {
        expect: 'the same BSON value the registry writes for the payload itself'
        encoded(value) == encoded(payload)

        where:
        value                                   | payload
        new Quantity(5)                         | 5
        new EMail("a@b.example")                | "a@b.example"
        new OrderDate(LocalDate.of(2024, 1, 1)) | LocalDate.of(2024, 1, 1)
    }

    def "P1 asRegistry() rejects a sub-document instead of decoding it into #type.simpleName via the canonical constructor"() {
        given: 'a field holding the sub-document the record codec writes'
        def reader = new BsonDocumentReader(new BsonDocument("v", subDocument))
        reader.readStartDocument()
        reader.readName()

        when:
        def decoded = registry.get(type).decode(reader, DecoderContext.builder().build())

        then: 'the payload codec refuses the document, as it does for any non-scalar'
        def e = thrown(RuntimeException)
        e instanceof BSONException || e instanceof CodecConfigurationException

        where:
        type      | subDocument
        Quantity  | new BsonDocument("value", new BsonInt32(5))
        EMail     | new BsonDocument("value", new BsonString("a@b.example"))
        OrderDate | new BsonDocument("date", new BsonDateTime(LocalDate.of(2024, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()))
    }

    /** Encodes {@code value} as a field with the codec the registry has for its class. */
    private BsonValue encoded(Object value) {
        Codec codec = registry.get(value.getClass())
        def document = new BsonDocument()
        def writer = new BsonDocumentWriter(document)
        writer.writeStartDocument()
        writer.writeName("v")
        codec.encode(writer, value, EncoderContext.builder().build())
        writer.writeEndDocument()
        return document.get("v")
    }
}
