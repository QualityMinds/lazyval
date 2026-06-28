package test

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleDeserializers
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.module.SimpleSerializers
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.time.LocalDate
import kotlin.Any
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

  private class IdsProductIdSerializer : StdSerializer<Ids.ProductId>(Ids.ProductId::class.java) {
    override fun serialize(
      `value`: Ids.ProductId,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      gen.writeString(value.value)
    }
  }

  private class IsbnSerializer : StdSerializer<Isbn>(Isbn::class.java) {
    override fun serialize(
      `value`: Isbn,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      gen.writeString(value.value)
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

  private class OrderDateSerializer : StdSerializer<OrderDate>(OrderDate::class.java) {
    private var innerSerializer: JsonSerializer<Any>? = null

    override fun serialize(
      `value`: OrderDate,
      gen: JsonGenerator,
      ctx: SerializerProvider,
    ) {
      val ser = innerSerializer ?: ctx.findValueSerializer(LocalDate::class.java).also { innerSerializer = it }
      ser.serialize(value.date, gen, ctx)
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

  private class IdsProductIdDeserializer : StdDeserializer<Ids.ProductId>(Ids.ProductId::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Ids.ProductId {
      val value = p.valueAsString
      return Ids.ProductId.of(value)
    }
  }

  private class IsbnDeserializer : StdDeserializer<Isbn>(Isbn::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Isbn {
      val value = p.valueAsString
      return Isbn.parse(value)
    }
  }

  private class NullableQuantityDeserializer : StdDeserializer<NullableQuantity?>(NullableQuantity::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): NullableQuantity? {
      val value = p.valueAsInt
      return NullableQuantity.ofNullable(value)
    }
  }

  private class OrderDateDeserializer : StdDeserializer<OrderDate>(OrderDate::class.java) {
    private var innerDeserializer: JsonDeserializer<Any>? = null

    override fun deserialize(p: JsonParser, ctx: DeserializationContext): OrderDate {
      val deser = innerDeserializer ?: ctx.findContextualValueDeserializer(ctx.constructType(LocalDate::class.java), null).also { innerDeserializer = it }
      val value = deser.deserialize(p, ctx) as LocalDate
      return OrderDate(value)
    }
  }

  private class QuantityDeserializer : StdDeserializer<Quantity>(Quantity::class.java) {
    override fun deserialize(p: JsonParser, ctx: DeserializationContext): Quantity {
      val value = p.valueAsInt
      return Quantity(value)
    }
  }
}
