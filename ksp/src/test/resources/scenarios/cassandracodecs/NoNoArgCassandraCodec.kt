package scenarios.cassandracodecs

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType

class NoNoArgCassandraCodec(private val prefix: String) :
    MappingCodec<String, String>(TypeCodecs.TEXT, GenericType.of(String::class.java)) {
    override fun innerToOuter(value: String?): String? = value?.let { prefix + it }
    override fun outerToInner(value: String?): String? = value
}
