package test.boundary.persistence.cassandra

import com.datastax.oss.driver.api.core.type.codec.MappingCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodec
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs
import com.datastax.oss.driver.api.core.type.reflect.GenericType
import jakarta.`annotation`.Generated
import java.time.LocalDate
import kotlin.Array
import kotlin.Int
import kotlin.String
import scenarios.kotlin.Ids
import scenarios.kotlin.Isbn
import scenarios.kotlin.NullableQuantity
import scenarios.kotlin.OrderDate
import scenarios.kotlin.Quantity

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.cassandra.CassandraCodecGenerator")
public object LazyvalCassandraCodecs {
  /**
   * Returns an array of all generated [TypeCodec]s for lazyval wrapper types.
   *
   * Use this method to register all codecs at once, e.g.:
   * ```
   * val session = CqlSession.builder()
   *     .addTypeCodecs(*LazyvalCassandraCodecs.all())
   *     .build()
   * ```
   *
   * User-supplied codecs configured via `lazyval.cassandra.codecs` are appended to the
   * array so they take precedence in DataStax's last-registered-wins resolution.
   *
   * @return an array containing one codec instance per generated wrapper type, followed
   * by one instance of each user-supplied codec
   */
  public fun all(): Array<TypeCodec<*>> = arrayOf(
      IdsProductIdCodec(),
      IsbnCodec(),
      NullableQuantityCodec(),
      OrderDateCodec(),
      QuantityCodec()
  )

  internal class IdsProductIdCodec : MappingCodec<String, Ids.ProductId?>(TypeCodecs.TEXT, object : GenericType<Ids.ProductId?>() {}) {
    override fun innerToOuter(`value`: String?): Ids.ProductId? = value?.let { Ids.ProductId.of(it) }

    override fun outerToInner(`value`: Ids.ProductId?): String? = value?.value
  }

  internal class IsbnCodec : MappingCodec<String, Isbn?>(TypeCodecs.TEXT, object : GenericType<Isbn?>() {}) {
    override fun innerToOuter(`value`: String?): Isbn? = value?.let { Isbn.parse(it) }

    override fun outerToInner(`value`: Isbn?): String? = value?.value
  }

  internal class NullableQuantityCodec : MappingCodec<Int, NullableQuantity?>(TypeCodecs.INT, object : GenericType<NullableQuantity?>() {}) {
    override fun innerToOuter(`value`: Int?): NullableQuantity? = value?.let { NullableQuantity.ofNullable(it) }

    override fun outerToInner(`value`: NullableQuantity?): Int? = value?.value
  }

  internal class OrderDateCodec : MappingCodec<LocalDate, OrderDate?>(TypeCodecs.DATE, object : GenericType<OrderDate?>() {}) {
    override fun innerToOuter(`value`: LocalDate?): OrderDate? = value?.let { OrderDate(it) }

    override fun outerToInner(`value`: OrderDate?): LocalDate? = value?.date
  }

  internal class QuantityCodec : MappingCodec<Int, Quantity?>(TypeCodecs.INT, object : GenericType<Quantity?>() {}) {
    override fun innerToOuter(`value`: Int?): Quantity? = value?.let { Quantity(it) }

    override fun outerToInner(`value`: Quantity?): Int? = value?.value
  }
}
