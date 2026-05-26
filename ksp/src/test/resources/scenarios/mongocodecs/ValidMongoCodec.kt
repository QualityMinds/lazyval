package scenarios.mongocodecs

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class ValidMongoCodec : Codec<String> {
    override fun encode(writer: BsonWriter, value: String, encoderContext: EncoderContext) {
        writer.writeString(value)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): String =
        reader.readString()

    override fun getEncoderClass(): Class<String> = String::class.java
}
