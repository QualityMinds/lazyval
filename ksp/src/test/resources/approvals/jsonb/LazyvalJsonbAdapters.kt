package test

import jakarta.`annotation`.Generated
import jakarta.json.bind.JsonbConfig
import jakarta.json.bind.adapter.JsonbAdapter
import java.time.LocalDate
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.jvm.JvmStatic
import scenarios.kotlin.Ids
import scenarios.kotlin.Isbn
import scenarios.kotlin.NullableQuantity
import scenarios.kotlin.OrderDate
import scenarios.kotlin.Quantity

public class LazyvalJsonbAdapters {
  public companion object {
    @JvmStatic
    public fun adapters(): Array<JsonbAdapter<*, *>> = arrayOf(
      IdsProductIdAdapter(),
      IsbnAdapter(),
      NullableQuantityAdapter(),
      OrderDateAdapter(),
      QuantityAdapter()
    )

    @JvmStatic
    public fun config(): JsonbConfig = JsonbConfig().withAdapters(*adapters())
  }

  @Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
  public class IdsProductIdAdapter : JsonbAdapter<Ids.ProductId, String> {
    override fun adaptToJson(obj: Ids.ProductId): String = obj.value

    override fun adaptFromJson(`value`: String): Ids.ProductId? = Ids.ProductId.of(value)
  }

  @Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
  public class IsbnAdapter : JsonbAdapter<Isbn, String> {
    override fun adaptToJson(obj: Isbn): String = obj.value

    override fun adaptFromJson(`value`: String): Isbn? = Isbn.parse(value)
  }

  @Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
  public class NullableQuantityAdapter : JsonbAdapter<NullableQuantity, Int> {
    override fun adaptToJson(obj: NullableQuantity): Int = obj.value

    override fun adaptFromJson(`value`: Int): NullableQuantity? = NullableQuantity.ofNullable(value)
  }

  @Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
  public class OrderDateAdapter : JsonbAdapter<OrderDate, LocalDate> {
    override fun adaptToJson(obj: OrderDate): LocalDate = obj.date

    override fun adaptFromJson(`value`: LocalDate): OrderDate? = OrderDate(value)
  }

  @Generated("com.qualityminds.lazyval.ksp.internal.codegen.JsonbGenerator")
  public class QuantityAdapter : JsonbAdapter<Quantity, Int> {
    override fun adaptToJson(obj: Quantity): Int = obj.value

    override fun adaptFromJson(`value`: Int): Quantity? = Quantity(value)
  }
}
