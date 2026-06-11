package scenarios.cassandracodecs

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType

internal class NonPublicCassandraCodec :
    MappingCodec<String, String>(TypeCodecs.TEXT, GenericType.of(String::class.java)) {
    override fun innerToOuter(value: String?): String? = value
    override fun outerToInner(value: String?): String? = value
}
