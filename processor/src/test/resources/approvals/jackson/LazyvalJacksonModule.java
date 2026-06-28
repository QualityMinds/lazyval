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
    static final IdsProductIdSerializer INSTANCE = new IdsProductIdSerializer();

    protected IdsProductIdSerializer() {
      super(Ids.ProductId.class);
    }

    @Override
    public void serialize(Ids.ProductId value, JsonGenerator gen, SerializationContext provider) {
      gen.writeString(value.value());
    }
  }

  static class IsbnSerializer extends StdSerializer<Isbn> {
    static final IsbnSerializer INSTANCE = new IsbnSerializer();

    protected IsbnSerializer() {
      super(Isbn.class);
    }

    @Override
    public void serialize(Isbn value, JsonGenerator gen, SerializationContext provider) {
      gen.writeString(value.getValue());
    }
  }

  static class OrderDateSerializer extends StdSerializer<OrderDate> {
    static final OrderDateSerializer INSTANCE = new OrderDateSerializer();

    private ValueSerializer<Object> innerSerializer;

    protected OrderDateSerializer() {
      super(OrderDate.class);
    }

    @Override
    public void serialize(OrderDate value, JsonGenerator gen, SerializationContext provider) {
      ValueSerializer<Object> ser = this.innerSerializer;
      if (ser == null) {
        ser = provider.findValueSerializer(LocalDate.class);
        this.innerSerializer = ser;
      }
      ser.serialize(value.value(), gen, provider);
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
    static final IdsProductIdDeserializer INSTANCE = new IdsProductIdDeserializer();

    protected IdsProductIdDeserializer() {
      super(Ids.ProductId.class);
    }

    @Override
    public Ids.ProductId deserialize(JsonParser p, DeserializationContext ctxt) {
      var value = p.getValueAsString();
      return Ids.ProductId.of(value);
    }
  }

  static class IsbnDeserializer extends StdDeserializer<Isbn> {
    static final IsbnDeserializer INSTANCE = new IsbnDeserializer();

    protected IsbnDeserializer() {
      super(Isbn.class);
    }

    @Override
    public Isbn deserialize(JsonParser p, DeserializationContext ctxt) {
      var value = p.getValueAsString();
      return Isbn.parse(value);
    }
  }

  static class OrderDateDeserializer extends StdDeserializer<OrderDate> {
    static final OrderDateDeserializer INSTANCE = new OrderDateDeserializer();

    private ValueDeserializer<Object> innerDeserializer;

    protected OrderDateDeserializer() {
      super(OrderDate.class);
    }

    @Override
    public OrderDate deserialize(JsonParser p, DeserializationContext ctxt) {
      ValueDeserializer<Object> deser = this.innerDeserializer;
      if (deser == null) {
        deser = ctxt.findContextualValueDeserializer(ctxt.constructType(LocalDate.class), null);
        this.innerDeserializer = deser;
      }
      var value = (LocalDate) deser.deserialize(p, ctxt);
      return new OrderDate(value);
    }
  }

  static class QuantityDeserializer extends StdDeserializer<Quantity> {
    static final QuantityDeserializer INSTANCE = new QuantityDeserializer();

    protected QuantityDeserializer() {
      super(Quantity.class);
    }

    @Override
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) {
      var value = p.getValueAsInt();
      return new Quantity(value);
    }
  }
}
