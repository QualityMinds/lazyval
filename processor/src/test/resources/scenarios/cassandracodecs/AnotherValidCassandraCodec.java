package scenarios.cassandracodecs;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

public class AnotherValidCassandraCodec extends MappingCodec<Integer, Integer> {

    public AnotherValidCassandraCodec() {
        super(TypeCodecs.INT, GenericType.of(Integer.class));
    }

    @Override
    protected Integer innerToOuter(Integer value) {
        return value;
    }

    @Override
    protected Integer outerToInner(Integer value) {
        return value;
    }
}
