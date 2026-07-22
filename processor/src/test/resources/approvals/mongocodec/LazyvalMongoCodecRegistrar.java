package test.boundary.persistence.mongodb;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

import javax.annotation.processing.Generated;

/**
 * A Quarkus {@link CodecProvider} CDI bean that delegates to {@link LazyvalMongoCodecs}; Quarkus auto-discovers it and
 * chains it into the default Mongo registry.
 *
 * <p>Like the delegate, {@code get} does not cache generated codecs (for generated types it returns a fresh codec per call).
 * The driver's registry ({@code ProvidersCodecRegistry}) memoizes the result per
 * {@code (Class, typeArguments)}, so it is consulted at most once per type per registry, not
 * per {@code encode}/{@code decode}. User-supplied codecs (via {@code lazyval.mongodb.codecs}) are instantiated once and returned as-is.
 */
@Generated("com.qualityminds.lazyval.processor.internal.codegen.mongo.MongoCodecGenerator")
@ApplicationScoped
@Unremovable
public class LazyvalMongoCodecRegistrar implements CodecProvider {
  private final LazyvalMongoCodecs delegate;

  public LazyvalMongoCodecRegistrar() {
    this.delegate = new LazyvalMongoCodecs();
  }

  @Override
  public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
    return delegate.get(clazz, registry);
  }
}
