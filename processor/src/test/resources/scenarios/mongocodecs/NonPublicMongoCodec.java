package scenarios.mongocodecs;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

class NonPublicMongoCodec implements Codec<String> {

    public NonPublicMongoCodec() {
    }

    @Override
    public void encode(BsonWriter writer, String value, EncoderContext encoderContext) {
        writer.writeString(value);
    }

    @Override
    public String decode(BsonReader reader, DecoderContext decoderContext) {
        return reader.readString();
    }

    @Override
    public Class<String> getEncoderClass() {
        return String.class;
    }
}
