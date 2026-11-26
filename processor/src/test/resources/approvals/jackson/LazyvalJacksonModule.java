package test;

import java.time.LocalDate;
import javax.annotation.processing.Generated;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleDeserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.module.SimpleSerializers;
import tools.jackson.databind.ser.std.StdSerializer;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.jackson.Jackson3Generator")
public class LazyvalJacksonModule extends SimpleModule {
  public LazyvalJacksonModule() {
    super("LazyvalJacksonModule", Version.unknownVersion());
  }

  @Override
  public void setupModule(JacksonModule.SetupContext context) {
    super.setupModule(context);
    SimpleSerializers sers = new SimpleSerializers();
    SimpleDeserializers desers = new SimpleDeserializers();
    sers.addSerializer(Ids.ProductId.class, IdsProductIdSerializer.INSTANCE);
    sers.addSerializer(Isbn.class, IsbnSerializer.INSTANCE);
    sers.addSerializer(OrderDate.class, OrderDateSerializer.INSTANCE);
    sers.addSerializer(Quantity.class, QuantitySerializer.INSTANCE);
    desers.addDeserializer(Ids.ProductId.class, IdsProductIdDeserializer.INSTANCE);
    desers.addDeserializer(Isbn.class, IsbnDeserializer.INSTANCE);
    desers.addDeserializer(OrderDate.class, OrderDateDeserializer.INSTANCE);
    desers.addDeserializer(Quantity.class, QuantityDeserializer.INSTANCE);
    context.addSerializers(sers);
    context.addDeserializers(desers);
  }

  static class IdsProductIdSerializer extends StdSerializer<Ids.ProductId> {
    static final IdsProductIdSerializer INSTANCE = new IdsProductIdSerializer(null);

    private final ValueSerializer<Object> innerSerializer;

    protected IdsProductIdSerializer(ValueSerializer<Object> innerSerializer) {
      super(Ids.ProductId.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext provider,
        BeanProperty property) {
      return new IdsProductIdSerializer(provider.findContentValueSerializer(String.class, property));
    }

    @Override
    public void serialize(Ids.ProductId value, JsonGenerator gen, SerializationContext provider) {
      innerSerializer.serialize(value.value(), gen, provider);
    }
  }

  static class IsbnSerializer extends StdSerializer<Isbn> {
    static final IsbnSerializer INSTANCE = new IsbnSerializer(null);

    private final ValueSerializer<Object> innerSerializer;

    protected IsbnSerializer(ValueSerializer<Object> innerSerializer) {
      super(Isbn.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext provider,
        BeanProperty property) {
      return new IsbnSerializer(provider.findContentValueSerializer(String.class, property));
    }

    @Override
    public void serialize(Isbn value, JsonGenerator gen, SerializationContext provider) {
      innerSerializer.serialize(value.getValue(), gen, provider);
    }
  }

  static class OrderDateSerializer extends StdSerializer<OrderDate> {
    static final OrderDateSerializer INSTANCE = new OrderDateSerializer(null);

    private final ValueSerializer<Object> innerSerializer;

    protected OrderDateSerializer(ValueSerializer<Object> innerSerializer) {
      super(OrderDate.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext provider,
        BeanProperty property) {
      return new OrderDateSerializer(provider.findContentValueSerializer(LocalDate.class, property));
    }

    @Override
    public void serialize(OrderDate value, JsonGenerator gen, SerializationContext provider) {
      innerSerializer.serialize(value.value(), gen, provider);
    }
  }

  static class QuantitySerializer extends StdSerializer<Quantity> {
    static final QuantitySerializer INSTANCE = new QuantitySerializer();

    protected QuantitySerializer() {
      super(Quantity.class);
    }

    @Override
    public void serialize(Quantity value, JsonGenerator gen, SerializationContext provider) {
      gen.writeNumber(value.value());
    }
  }

  static class IdsProductIdDeserializer extends StdDeserializer<Ids.ProductId> {
    static final IdsProductIdDeserializer INSTANCE = new IdsProductIdDeserializer(null);

    private final ValueDeserializer<Object> innerDeserializer;

    protected IdsProductIdDeserializer(ValueDeserializer<Object> innerDeserializer) {
      super(Ids.ProductId.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
        BeanProperty property) {
      return new IdsProductIdDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(String.class), property));
    }

    @Override
    public Ids.ProductId deserialize(JsonParser p, DeserializationContext ctxt) {
      String value = (String) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return Ids.ProductId.of(value);
    }
  }

  static class IsbnDeserializer extends StdDeserializer<Isbn> {
    static final IsbnDeserializer INSTANCE = new IsbnDeserializer(null);

    private final ValueDeserializer<Object> innerDeserializer;

    protected IsbnDeserializer(ValueDeserializer<Object> innerDeserializer) {
      super(Isbn.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
        BeanProperty property) {
      return new IsbnDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(String.class), property));
    }

    @Override
    public Isbn deserialize(JsonParser p, DeserializationContext ctxt) {
      String value = (String) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return Isbn.parse(value);
    }
  }

  static class OrderDateDeserializer extends StdDeserializer<OrderDate> {
    static final OrderDateDeserializer INSTANCE = new OrderDateDeserializer(null);

    private final ValueDeserializer<Object> innerDeserializer;

    protected OrderDateDeserializer(ValueDeserializer<Object> innerDeserializer) {
      super(OrderDate.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
        BeanProperty property) {
      return new OrderDateDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(LocalDate.class), property));
    }

    @Override
    public OrderDate deserialize(JsonParser p, DeserializationContext ctxt) {
      LocalDate value = (LocalDate) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return new OrderDate(value);
    }
  }

  static class QuantityDeserializer extends StdDeserializer<Quantity> {
    static final QuantityDeserializer INSTANCE = new QuantityDeserializer(null);

    private final ValueDeserializer<Object> innerDeserializer;

    protected QuantityDeserializer(ValueDeserializer<Object> innerDeserializer) {
      super(Quantity.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
        BeanProperty property) {
      return new QuantityDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(int.class), property));
    }

    @Override
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) {
      int value;
      if (p.isExpectedNumberIntToken()) {
        value = p.getIntValue();
      } else {
        value = (Integer) innerDeserializer.deserialize(p, ctxt);
      }
      return Quantity.of(value);
    }
  }
}
