package scenarios.mongocodecs

import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class AnotherValidMongoCodec : Codec<Int> {
    override fun encode(writer: BsonWriter, value: Int, encoderContext: EncoderContext) {
        writer.writeInt32(value)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Int =
        reader.readInt32()

    override fun getEncoderClass(): Class<Int> = Int::class.javaObjectType
}
