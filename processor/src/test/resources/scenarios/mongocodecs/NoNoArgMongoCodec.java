package scenarios.mongocodecs;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class NoNoArgMongoCodec implements Codec<String> {

    private final String prefix;

    public NoNoArgMongoCodec(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void encode(BsonWriter writer, String value, EncoderContext encoderContext) {
        writer.writeString(prefix + value);
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
