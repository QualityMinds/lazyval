package test

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleDeserializers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.module.SimpleSerializers
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.time.LocalDate
import kotlin.Any
import kotlin.Int
import kotlin.String
import scenarios.kotlin.Ids
import scenarios.kotlin.Isbn
import scenarios.kotlin.NullableQuantity
import scenarios.kotlin.OrderDate
import scenarios.kotlin.Quantity

public class LazyvalJackson2Module : SimpleModule("LazyvalJackson2Module", Version.unknownVersion()) {
  override fun setupModule(context: Module.SetupContext) {
    super.setupModule(context)
    val sers = SimpleSerializers()
    val desers = SimpleDeserializers()
    sers.addSerializer(Ids.ProductId::class.java, idsProductIdSerializer)
    sers.addSerializer(Isbn::class.java, isbnSerializer)
    sers.addSerializer(NullableQuantity::class.java, nullableQuantitySerializer)
    sers.addSerializer(OrderDate::class.java, orderDateSerializer)
    sers.addSerializer(Quantity::class.java, quantitySerializer)
    desers.addDeserializer(Ids.ProductId::class.java, idsProductIdDeserializer)
    desers.addDeserializer(Isbn::class.java, isbnDeserializer)
    desers.addDeserializer(NullableQuantity::class.java, nullableQuantityDeserializer)
    desers.addDeserializer(OrderDate::class.java, orderDateDeserializer)
    desers.addDeserializer(Quantity::class.java, quantityDeserializer)
    context.addSerializers(sers)
    context.addDeserializers(desers)
  }

  public companion object {
    private val idsProductIdSerializer: IdsProductIdSerializer = IdsProductIdSerializer()

    private val isbnSerializer: IsbnSerializer = IsbnSerializer()

    private val nullableQuantitySerializer: NullableQuantitySerializer =
        NullableQuantitySerializer()

    private val orderDateSerializer: OrderDateSerializer = OrderDateSerializer()

    private val quantitySerializer: QuantitySerializer = QuantitySerializer()

    private val idsProductIdDeserializer: IdsProductIdDeserializer = IdsProductIdDeserializer()

    private val isbnDeserializer: IsbnDeserializer = IsbnDeserializer()

    private val nullableQuantityDeserializer: NullableQuantityDeserializer =
        NullableQuantityDeserializer()

    private val orderDateDeserializer: OrderDateDeserializer = OrderDateDeserializer()

    private val quantityDeserializer: QuantityDeserializer = QuantityDeserializer()
  }

  private class IdsProductIdSerializer(
    private val innerSerializer: JsonSerializer<Any>? = null,
  ) : StdSerializer<Ids.ProductId>(Ids.ProductId::class.java),
      ContextualSerializer {
    override fun createContextual(ctx: SerializerProvider, `property`: BeanProperty?): JsonSerializer<*> = IdsProductIdSerializer(ctx.findContentValueSerializer(String::class.java, property))

    override fun serialize(
      `value`: Ids.ProductId,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      checkNotNull(innerSerializer).serialize(value.value, gen, ctx)
    }
  }

  private class IsbnSerializer(
    private val innerSerializer: JsonSerializer<Any>? = null,
  ) : StdSerializer<Isbn>(Isbn::class.java),
      ContextualSerializer {
    override fun createContextual(ctx: SerializerProvider, `property`: BeanProperty?): JsonSerializer<*> = IsbnSerializer(ctx.findContentValueSerializer(String::class.java, property))

    override fun serialize(
      `value`: Isbn,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      checkNotNull(innerSerializer).serialize(value.value, gen, ctx)
    }
  }

  private class NullableQuantitySerializer : StdSerializer<NullableQuantity>(NullableQuantity::class.java) {
    override fun serialize(
      `value`: NullableQuantity,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      gen.writeNumber(value.value)
    }
  }

  private class OrderDateSerializer(
    private val innerSerializer: JsonSerializer<Any>? = null,
  ) : StdSerializer<OrderDate>(OrderDate::class.java),
      ContextualSerializer {
    override fun createContextual(ctx: SerializerProvider, `property`: BeanProperty?): JsonSerializer<*> = OrderDateSerializer(ctx.findContentValueSerializer(LocalDate::class.java, property))

    override fun serialize(
      `value`: OrderDate,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      checkNotNull(innerSerializer).serialize(value.date, gen, ctx)
    }
  }

  private class QuantitySerializer : StdSerializer<Quantity>(Quantity::class.java) {
    override fun serialize(
      `value`: Quantity,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      gen.writeNumber(value.value)
    }
  }

  private class IdsProductIdDeserializer(
    private val innerDeserializer: JsonDeserializer<Any>? = null,
  ) : StdDeserializer<Ids.ProductId>(Ids.ProductId::class.java),
      ContextualDeserializer {
    override fun createContextual(ctx: DeserializationContext, `property`: BeanProperty?): JsonDeserializer<*> = IdsProductIdDeserializer(ctx.findContextualValueDeserializer(ctx.constructType(String::class.java), property))

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Ids.ProductId {
      val value = checkNotNull(innerDeserializer).deserialize(p, ctx) as String
      return Ids.ProductId.of(value)
    }
  }

  private class IsbnDeserializer(
    private val innerDeserializer: JsonDeserializer<Any>? = null,
  ) : StdDeserializer<Isbn>(Isbn::class.java),
      ContextualDeserializer {
    override fun createContextual(ctx: DeserializationContext, `property`: BeanProperty?): JsonDeserializer<*> = IsbnDeserializer(ctx.findContextualValueDeserializer(ctx.constructType(String::class.java), property))

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Isbn {
      val value = checkNotNull(innerDeserializer).deserialize(p, ctx) as String
      return Isbn.parse(value)
    }
  }

  private class NullableQuantityDeserializer(
    private val innerDeserializer: JsonDeserializer<Any>? = null,
  ) : StdDeserializer<NullableQuantity?>(NullableQuantity::class.java),
      ContextualDeserializer {
    override fun createContextual(ctx: DeserializationContext, `property`: BeanProperty?): JsonDeserializer<*> = NullableQuantityDeserializer(ctx.findContextualValueDeserializer(ctx.constructType(Int::class.java), property))

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): NullableQuantity? {
      val value = if (p.isExpectedNumberIntToken) {
        p.intValue
      } else {
        checkNotNull(innerDeserializer).deserialize(p, ctx) as Int
      }
      return NullableQuantity.ofNullable(value)
    }
  }

  private class OrderDateDeserializer(
    private val innerDeserializer: JsonDeserializer<Any>? = null,
  ) : StdDeserializer<OrderDate>(OrderDate::class.java),
      ContextualDeserializer {
    override fun createContextual(ctx: DeserializationContext, `property`: BeanProperty?): JsonDeserializer<*> = OrderDateDeserializer(ctx.findContextualValueDeserializer(ctx.constructType(LocalDate::class.java), property))

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): OrderDate {
      val value = checkNotNull(innerDeserializer).deserialize(p, ctx) as LocalDate
      return OrderDate(value)
    }
  }

  private class QuantityDeserializer(
    private val innerDeserializer: JsonDeserializer<Any>? = null,
  ) : StdDeserializer<Quantity>(Quantity::class.java),
      ContextualDeserializer {
    override fun createContextual(ctx: DeserializationContext, `property`: BeanProperty?): JsonDeserializer<*> = QuantityDeserializer(ctx.findContextualValueDeserializer(ctx.constructType(Int::class.java), property))

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Quantity {
      val value = if (p.isExpectedNumberIntToken) {
        p.intValue
      } else {
        checkNotNull(innerDeserializer).deserialize(p, ctx) as Int
      }
      return Quantity.of(value)
    }
  }
}
