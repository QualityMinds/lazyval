package test.boundary.persistence.mongodb

import jakarta.`annotation`.Generated
import java.lang.Class
import java.time.LocalDate
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistry
import scenarios.kotlin.Ids
import scenarios.kotlin.Isbn
import scenarios.kotlin.NullableQuantity
import scenarios.kotlin.OrderDate
import scenarios.kotlin.Quantity

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.mongo.MongoCodecGenerator")
public class LazyvalMongoCodecs() : CodecProvider {
  private val userCodecs: Array<Codec<*>> = arrayOf()

  @Suppress("UNCHECKED_CAST")
  override fun <T> `get`(clazz: Class<T>, registry: CodecRegistry): Codec<T>? {
    if (clazz == Ids.ProductId::class.java) {
        return IdsProductIdCodec(registry.get(String::class.javaObjectType)) as Codec<T>?
    }
    if (clazz == Isbn::class.java) {
        return IsbnCodec(registry.get(String::class.javaObjectType)) as Codec<T>?
    }
    if (clazz == NullableQuantity::class.java) {
        return NullableQuantityCodec(registry.get(Int::class.javaObjectType)) as Codec<T>?
    }
    if (clazz == OrderDate::class.java) {
        return OrderDateCodec(registry.get(LocalDate::class.javaObjectType)) as Codec<T>?
    }
    if (clazz == Quantity::class.java) {
        return QuantityCodec(registry.get(Int::class.javaObjectType)) as Codec<T>?
    }
    return null
  }

  /**
   * BSON codec for [Ids.ProductId]. Follows the MongoDB driver convention: invoked only on non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) filter nulls at the document/element level before invoking property codecs. Direct invocation with a null value or a reader at a BSON NULL token is a contract violation and the behavior is undefined.
   */
  internal class IdsProductIdCodec(
    private val innerCodec: Codec<String>,
  ) : Codec<Ids.ProductId> {
    override fun encode(
      writer: BsonWriter,
      `value`: Ids.ProductId,
      encoderContext: EncoderContext,
    ) {
      innerCodec.encode(writer, value.value, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Ids.ProductId = Ids.ProductId.of(innerCodec.decode(reader, decoderContext))

    override fun getEncoderClass(): Class<Ids.ProductId> = Ids.ProductId::class.java
  }

  /**
   * BSON codec for [Isbn]. Follows the MongoDB driver convention: invoked only on non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) filter nulls at the document/element level before invoking property codecs. Direct invocation with a null value or a reader at a BSON NULL token is a contract violation and the behavior is undefined.
   */
  internal class IsbnCodec(
    private val innerCodec: Codec<String>,
  ) : Codec<Isbn> {
    override fun encode(
      writer: BsonWriter,
      `value`: Isbn,
      encoderContext: EncoderContext,
    ) {
      innerCodec.encode(writer, value.value, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Isbn = Isbn.parse(innerCodec.decode(reader, decoderContext))

    override fun getEncoderClass(): Class<Isbn> = Isbn::class.java
  }

  /**
   * BSON codec for [NullableQuantity]. Follows the MongoDB driver convention: invoked only on non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) filter nulls at the document/element level before invoking property codecs. Direct invocation with a null value or a reader at a BSON NULL token is a contract violation and the behavior is undefined.
   */
  internal class NullableQuantityCodec(
    private val innerCodec: Codec<Int>,
  ) : Codec<NullableQuantity?> {
    override fun encode(
      writer: BsonWriter,
      `value`: NullableQuantity?,
      encoderContext: EncoderContext,
    ) {
      if (value == null) {
          writer.writeNull()
          return
      }
      innerCodec.encode(writer, value.value, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): NullableQuantity? = NullableQuantity.ofNullable(innerCodec.decode(reader, decoderContext))

    @Suppress("UNCHECKED_CAST")
    override fun getEncoderClass(): Class<NullableQuantity?> = NullableQuantity::class.java as Class<NullableQuantity?>
  }

  /**
   * BSON codec for [OrderDate]. Follows the MongoDB driver convention: invoked only on non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) filter nulls at the document/element level before invoking property codecs. Direct invocation with a null value or a reader at a BSON NULL token is a contract violation and the behavior is undefined.
   */
  internal class OrderDateCodec(
    private val innerCodec: Codec<LocalDate>,
  ) : Codec<OrderDate> {
    override fun encode(
      writer: BsonWriter,
      `value`: OrderDate,
      encoderContext: EncoderContext,
    ) {
      innerCodec.encode(writer, value.date, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): OrderDate = OrderDate(innerCodec.decode(reader, decoderContext))

    override fun getEncoderClass(): Class<OrderDate> = OrderDate::class.java
  }

  /**
   * BSON codec for [Quantity]. Follows the MongoDB driver convention: invoked only on non-null BSON tokens. Standard call paths (PojoCodec, IterableCodec, ...) filter nulls at the document/element level before invoking property codecs. Direct invocation with a null value or a reader at a BSON NULL token is a contract violation and the behavior is undefined.
   */
  internal class QuantityCodec(
    private val innerCodec: Codec<Int>,
  ) : Codec<Quantity> {
    override fun encode(
      writer: BsonWriter,
      `value`: Quantity,
      encoderContext: EncoderContext,
    ) {
      innerCodec.encode(writer, value.value, encoderContext)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): Quantity = Quantity(innerCodec.decode(reader, decoderContext))

    override fun getEncoderClass(): Class<Quantity> = Quantity::class.java
  }
}
