package scenarios.cassandracodecs;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

public class ValidCassandraCodec extends MappingCodec<String, String> {

    public ValidCassandraCodec() {
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
