package test.boundary.persistence.mongodb;

import com.mongodb.MongoClientSettings;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

import javax.annotation.processing.Generated;
import java.time.LocalDate;

/**
 * A {@link CodecProvider} with one native MongoDB {@code Codec} per generated domain-primitive.
 *
 * <p>{@code get} intentionally does not cache generated codecs: for generated types it returns a fresh codec on every call.
 * The MongoDB registry ({@code ProvidersCodecRegistry}) already memoizes the result per
 * {@code (Class, typeArguments)}, so it is invoked at most once per type per registry, not per
 * {@code encode}/{@code decode}. Caching here would be redundant, and since each generated codec is bound
 * to the {@code registry} it was built from, could return a codec wired to the wrong registry.
 * <p>User-supplied codecs (via {@code lazyval.mongodb.codecs}) are instantiated once and returned as-is.
 */
@Generated("com.qualityminds.lazyval.processor.internal.codegen.mongo.MongoCodecGenerator")
public final class LazyvalMongoCodecs implements CodecProvider {
  private final Codec<?>[] userCodecs;

  public LazyvalMongoCodecs() {
    this.userCodecs = new Codec<?>[0];
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
    if (clazz == Ids.ProductId.class) {
      return (Codec<T>) new IdsProductIdCodec(registry.get(String.class));
    }
    if (clazz == Isbn.class) {
      return (Codec<T>) new IsbnCodec(registry.get(String.class));
    }
    if (clazz == OrderDate.class) {
      return (Codec<T>) new OrderDateCodec(registry.get(LocalDate.class));
    }
    if (clazz == Quantity.class) {
      return (Codec<T>) new QuantityCodec(registry.get(Integer.class));
    }
    return null;
  }

  /**
   * Convenience method returning a {@link CodecRegistry} that combines the default Mongo
   * registry with this provider. Use it for one-line setup outside of CDI:
   * <pre>{@code
   * MongoClientSettings settings = MongoClientSettings.builder()
   *     .codecRegistry(LazyvalMongoCodecs.asRegistry())
   *     .build();
   * }</pre>
   *
   * @return a {@code CodecRegistry} with the default registry and the generated codecs
   */
  public static CodecRegistry asRegistry() {
    return CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), CodecRegistries.fromProviders(new LazyvalMongoCodecs()));
  }

  static class IdsProductIdCodec implements Codec<Ids.ProductId> {
    private final Codec<String> innerCodec;

    public IdsProductIdCodec(Codec<String> innerCodec) {
      this.innerCodec = innerCodec;
    }

    @Override
    public void encode(BsonWriter writer, Ids.ProductId value, EncoderContext encoderContext) {
      innerCodec.encode(writer, value.value(), encoderContext);
    }

    @Override
    public Ids.ProductId decode(BsonReader reader, DecoderContext decoderContext) {
      return Ids.ProductId.of(innerCodec.decode(reader, decoderContext));
    }

    @Override
    public Class<Ids.ProductId> getEncoderClass() {
      return Ids.ProductId.class;
    }
  }

  static class IsbnCodec implements Codec<Isbn> {
    private final Codec<String> innerCodec;

    public IsbnCodec(Codec<String> innerCodec) {
      this.innerCodec = innerCodec;
    }

    @Override
    public void encode(BsonWriter writer, Isbn value, EncoderContext encoderContext) {
      innerCodec.encode(writer, value.getValue(), encoderContext);
    }

    @Override
    public Isbn decode(BsonReader reader, DecoderContext decoderContext) {
      return Isbn.parse(innerCodec.decode(reader, decoderContext));
    }

    @Override
    public Class<Isbn> getEncoderClass() {
      return Isbn.class;
    }
  }

  static class OrderDateCodec implements Codec<OrderDate> {
    private final Codec<LocalDate> innerCodec;

    public OrderDateCodec(Codec<LocalDate> innerCodec) {
      this.innerCodec = innerCodec;
    }

    @Override
    public void encode(BsonWriter writer, OrderDate value, EncoderContext encoderContext) {
      innerCodec.encode(writer, value.value(), encoderContext);
    }

    @Override
    public OrderDate decode(BsonReader reader, DecoderContext decoderContext) {
      return new OrderDate(innerCodec.decode(reader, decoderContext));
    }

    @Override
    public Class<OrderDate> getEncoderClass() {
      return OrderDate.class;
    }
  }

  static class QuantityCodec implements Codec<Quantity> {
    private final Codec<Integer> innerCodec;

    public QuantityCodec(Codec<Integer> innerCodec) {
      this.innerCodec = innerCodec;
    }

    @Override
    public void encode(BsonWriter writer, Quantity value, EncoderContext encoderContext) {
      innerCodec.encode(writer, value.value(), encoderContext);
    }

    @Override
    public Quantity decode(BsonReader reader, DecoderContext decoderContext) {
      return new Quantity(innerCodec.decode(reader, decoderContext));
    }

    @Override
    public Class<Quantity> getEncoderClass() {
      return Quantity.class;
    }
  }
}
