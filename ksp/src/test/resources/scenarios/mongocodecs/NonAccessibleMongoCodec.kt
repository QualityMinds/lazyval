package scenarios.mongocodecs

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

/**
 * Top-level `private` (file-scoped) class. Cannot be referenced from any other file,
 * regardless of module or package. Used to verify that the visibility check rejects
 * unconditionally inaccessible classes.
 */
private class NonAccessibleMongoCodec : Codec<String> {
    override fun encode(writer: BsonWriter, value: String, encoderContext: EncoderContext) {
        writer.writeString(value)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): String =
        reader.readString()

    override fun getEncoderClass(): Class<String> = String::class.java
}
