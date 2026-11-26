package test;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.jackson.Jackson2Generator")
public class LazyvalJackson2Module extends SimpleModule {
  public LazyvalJackson2Module() {
    super("LazyvalJackson2Module", Version.unknownVersion());
  }

  @Override
  public void setupModule(Module.SetupContext context) {
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

  static class IdsProductIdSerializer extends StdSerializer<Ids.ProductId> implements ContextualSerializer {
    static final IdsProductIdSerializer INSTANCE = new IdsProductIdSerializer(null);

    private final JsonSerializer<Object> innerSerializer;

    protected IdsProductIdSerializer(JsonSerializer<Object> innerSerializer) {
      super(Ids.ProductId.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
        throws JsonMappingException {
      return new IdsProductIdSerializer(provider.findContentValueSerializer(String.class, property));
    }

    @Override
    public void serialize(Ids.ProductId value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      innerSerializer.serialize(value.value(), gen, provider);
    }
  }

  static class IsbnSerializer extends StdSerializer<Isbn> implements ContextualSerializer {
    static final IsbnSerializer INSTANCE = new IsbnSerializer(null);

    private final JsonSerializer<Object> innerSerializer;

    protected IsbnSerializer(JsonSerializer<Object> innerSerializer) {
      super(Isbn.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
        throws JsonMappingException {
      return new IsbnSerializer(provider.findContentValueSerializer(String.class, property));
    }

    @Override
    public void serialize(Isbn value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      innerSerializer.serialize(value.getValue(), gen, provider);
    }
  }

  static class OrderDateSerializer extends StdSerializer<OrderDate> implements ContextualSerializer {
    static final OrderDateSerializer INSTANCE = new OrderDateSerializer(null);

    private final JsonSerializer<Object> innerSerializer;

    protected OrderDateSerializer(JsonSerializer<Object> innerSerializer) {
      super(OrderDate.class);
      this.innerSerializer = innerSerializer;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
        throws JsonMappingException {
      return new OrderDateSerializer(provider.findContentValueSerializer(LocalDate.class, property));
    }

    @Override
    public void serialize(OrderDate value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      innerSerializer.serialize(value.value(), gen, provider);
    }
  }

  static class QuantitySerializer extends StdSerializer<Quantity> {
    static final QuantitySerializer INSTANCE = new QuantitySerializer();

    protected QuantitySerializer() {
      super(Quantity.class);
    }

    @Override
    public void serialize(Quantity value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      gen.writeNumber(value.value());
    }
  }

  static class IdsProductIdDeserializer extends StdDeserializer<Ids.ProductId> implements ContextualDeserializer {
    static final IdsProductIdDeserializer INSTANCE = new IdsProductIdDeserializer(null);

    private final JsonDeserializer<Object> innerDeserializer;

    protected IdsProductIdDeserializer(JsonDeserializer<Object> innerDeserializer) {
      super(Ids.ProductId.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      return new IdsProductIdDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(String.class), property));
    }

    @Override
    public Ids.ProductId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      String value = (String) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return Ids.ProductId.of(value);
    }
  }

  static class IsbnDeserializer extends StdDeserializer<Isbn> implements ContextualDeserializer {
    static final IsbnDeserializer INSTANCE = new IsbnDeserializer(null);

    private final JsonDeserializer<Object> innerDeserializer;

    protected IsbnDeserializer(JsonDeserializer<Object> innerDeserializer) {
      super(Isbn.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      return new IsbnDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(String.class), property));
    }

    @Override
    public Isbn deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      String value = (String) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return Isbn.parse(value);
    }
  }

  static class OrderDateDeserializer extends StdDeserializer<OrderDate> implements ContextualDeserializer {
    static final OrderDateDeserializer INSTANCE = new OrderDateDeserializer(null);

    private final JsonDeserializer<Object> innerDeserializer;

    protected OrderDateDeserializer(JsonDeserializer<Object> innerDeserializer) {
      super(OrderDate.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      return new OrderDateDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(LocalDate.class), property));
    }

    @Override
    public OrderDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      LocalDate value = (LocalDate) innerDeserializer.deserialize(p, ctxt);
      if (value == null) {
        return null;
      }
      return new OrderDate(value);
    }
  }

  static class QuantityDeserializer extends StdDeserializer<Quantity> implements ContextualDeserializer {
    static final QuantityDeserializer INSTANCE = new QuantityDeserializer(null);

    private final JsonDeserializer<Object> innerDeserializer;

    protected QuantityDeserializer(JsonDeserializer<Object> innerDeserializer) {
      super(Quantity.class);
      this.innerDeserializer = innerDeserializer;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
        throws JsonMappingException {
      return new QuantityDeserializer(ctxt.findContextualValueDeserializer(ctxt.constructType(int.class), property));
    }

    @Override
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
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
