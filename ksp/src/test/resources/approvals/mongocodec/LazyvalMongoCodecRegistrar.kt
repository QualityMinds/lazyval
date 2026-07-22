package test.boundary.persistence.mongodb

import io.quarkus.arc.Unremovable
import jakarta.`annotation`.Generated
import jakarta.enterprise.context.ApplicationScoped
import java.lang.Class
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry

/**
 * A Quarkus [CodecProvider] CDI bean that delegates to [LazyvalMongoCodecs]; Quarkus auto-discovers it and chains it
 * into the default Mongo registry.
 *
 * Like the delegate, `get` does not cache generated codecs (for generated types it returns a fresh codec per call). The driver's
 * registry (`ProvidersCodecRegistry`) memoizes the result per `(Class, typeArguments)`, so it
 * is consulted at most once per type per registry, not per `encode`/`decode`.
 * User-supplied codecs (via `lazyval.mongodb.codecs`) are instantiated once and returned as-is.
 */
@Generated("com.qualityminds.lazyval.ksp.internal.codegen.mongo.MongoCodecGenerator")
@ApplicationScoped
@Unremovable
public class LazyvalMongoCodecRegistrar : CodecProvider {
  private val `delegate`: LazyvalMongoCodecs = LazyvalMongoCodecs()

  override fun <T> `get`(clazz: Class<T>, registry: CodecRegistry): Codec<T>? = delegate.get(clazz, registry)
}
