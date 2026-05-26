package scenarios.mongocodecs;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

/**
 * Holds a private nested {@link Codec} that cannot be reached from any package.
 * Used to verify the visibility check rejects unconditionally inaccessible classes.
 */
public final class NonAccessibleMongoCodec {

    private NonAccessibleMongoCodec() {
    }

    private static final class Inner implements Codec<String> {

        public Inner() {
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
}
