package scenarios.cassandracodecs

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType

class AnotherValidCassandraCodec : MappingCodec<Int, Int>(TypeCodecs.INT, GenericType.of(Int::class.javaObjectType)) {
    override fun innerToOuter(value: Int?): Int? = value
    override fun outerToInner(value: Int?): Int? = value
}
