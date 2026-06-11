package scenarios.cassandracodecs;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

class NonPublicCassandraCodec extends MappingCodec<String, String> {

    public NonPublicCassandraCodec() {
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
