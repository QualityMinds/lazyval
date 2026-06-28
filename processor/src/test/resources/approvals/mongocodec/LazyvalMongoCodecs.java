package test.boundary.persistence.mongodb;

import java.lang.Class;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

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
