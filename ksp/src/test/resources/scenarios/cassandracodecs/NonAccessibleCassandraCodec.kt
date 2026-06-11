package scenarios.cassandracodecs

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType

/**
 * Top-level `private` (file-scoped) class. Cannot be referenced from any other file,
 * regardless of module or package. Used to verify that the visibility check rejects
 * unconditionally inaccessible classes.
 */
private class NonAccessibleCassandraCodec :
    MappingCodec<String, String>(TypeCodecs.TEXT, GenericType.of(String::class.java)) {
    override fun innerToOuter(value: String?): String? = value
    override fun outerToInner(value: String?): String? = value
}
