package test;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
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

  static class IdsProductIdSerializer extends StdSerializer<Ids.ProductId> {
    static final IdsProductIdSerializer INSTANCE = new IdsProductIdSerializer();

    protected IdsProductIdSerializer() {
      super(Ids.ProductId.class);
    }

    @Override
    public void serialize(Ids.ProductId value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
      gen.writeString(value.value());
    }
  }

  static class IsbnSerializer extends StdSerializer<Isbn> {
    static final IsbnSerializer INSTANCE = new IsbnSerializer();

    protected IsbnSerializer() {
      super(Isbn.class);
    }

    @Override
    public void serialize(Isbn value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      gen.writeString(value.getValue());
    }
  }

  static class OrderDateSerializer extends StdSerializer<OrderDate> {
    static final OrderDateSerializer INSTANCE = new OrderDateSerializer();

    private JsonSerializer<Object> innerSerializer;

    protected OrderDateSerializer() {
      super(OrderDate.class);
    }

    @Override
    public void serialize(OrderDate value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      JsonSerializer<Object> ser = this.innerSerializer;
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
    public void serialize(Quantity value, JsonGenerator gen, SerializerProvider provider) throws
        IOException {
      gen.writeNumber(value.value());
    }
  }

  static class IdsProductIdDeserializer extends StdDeserializer<Ids.ProductId> {
    static final IdsProductIdDeserializer INSTANCE = new IdsProductIdDeserializer();

    protected IdsProductIdDeserializer() {
      super(Ids.ProductId.class);
    }

    @Override
    public Ids.ProductId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
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
    public Isbn deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      var value = p.getValueAsString();
      return Isbn.parse(value);
    }
  }

  static class OrderDateDeserializer extends StdDeserializer<OrderDate> {
    static final OrderDateDeserializer INSTANCE = new OrderDateDeserializer();

    private JsonDeserializer<Object> innerDeserializer;

    protected OrderDateDeserializer() {
      super(OrderDate.class);
    }

    @Override
    public OrderDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      JsonDeserializer<Object> deser = this.innerDeserializer;
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
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException,
        JacksonException {
      var value = p.getValueAsInt();
      return new Quantity(value);
    }
  }
}
