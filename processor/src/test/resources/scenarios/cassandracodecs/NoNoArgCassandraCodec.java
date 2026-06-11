package scenarios.cassandracodecs;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

public class NoNoArgCassandraCodec extends MappingCodec<String, String> {

    private final String prefix;

    public NoNoArgCassandraCodec(String prefix) {
        super(TypeCodecs.TEXT, GenericType.of(String.class));
        this.prefix = prefix;
    }

    @Override
    protected String innerToOuter(String value) {
        return prefix + value;
    }

    @Override
    protected String outerToInner(String value) {
        return value;
    }
}
