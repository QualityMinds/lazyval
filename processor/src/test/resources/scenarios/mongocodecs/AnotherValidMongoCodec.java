package scenarios.mongocodecs;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class AnotherValidMongoCodec implements Codec<Integer> {

    public AnotherValidMongoCodec() {
    }

    @Override
    public void encode(BsonWriter writer, Integer value, EncoderContext encoderContext) {
        writer.writeInt32(value);
    }

    @Override
    public Integer decode(BsonReader reader, DecoderContext decoderContext) {
        return reader.readInt32();
    }

    @Override
    public Class<Integer> getEncoderClass() {
        return Integer.class;
    }
}
