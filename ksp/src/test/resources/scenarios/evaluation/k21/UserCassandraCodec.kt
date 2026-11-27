package evaluation.k21

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType

/** A user codec listed in `lazyval.cassandra.codecs`, which switches on the generated override detection. */
class UserCassandraCodec : MappingCodec<String, String>(TypeCodecs.TEXT, GenericType.of(String::class.java)) {
    override fun innerToOuter(value: String?): String? = value
    override fun outerToInner(value: String?): String? = value
}
