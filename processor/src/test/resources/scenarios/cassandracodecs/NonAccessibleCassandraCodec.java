package scenarios.cassandracodecs;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

/**
 * Holds a private nested {@code TypeCodec} that cannot be reached from any package.
 * Used to verify the visibility check rejects unconditionally inaccessible classes.
 */
public final class NonAccessibleCassandraCodec {

    private NonAccessibleCassandraCodec() {
    }

    private static final class Inner extends MappingCodec<String, String> {

        public Inner() {
            super(TypeCodecs.TEXT, GenericType.of(String.class));
        }

        @Override
        protected String innerToOuter(String value) {
            return value;
        }

        @Override
        protected String outerToInner(String value) {
            return value;
        }
    }
}
