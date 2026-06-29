package test;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

import javax.annotation.processing.Generated;
import java.time.LocalDate;

public class LazyvalJsonbAdapters {
  public static JsonbAdapter[] adapters() {
    return new JsonbAdapter[]{
            new IdsProductIdAdapter(),
            new IsbnAdapter(),
            new OrderDateAdapter(),
            new QuantityAdapter()
        };
  }

  public static JsonbConfig config() {
    return new JsonbConfig().withAdapters(adapters());
  }

  @Generated("com.qualityminds.lazyval.processor.internal.codegen.JsonbGenerator")
  static class IdsProductIdAdapter implements JsonbAdapter<Ids.ProductId, String> {
    @Override
    public String adaptToJson(Ids.ProductId obj) throws Exception {
      return obj.value();
    }

    @Override
    public Ids.ProductId adaptFromJson(String value) throws Exception {
      return Ids.ProductId.of(value);
    }
  }

  @Generated("com.qualityminds.lazyval.processor.internal.codegen.JsonbGenerator")
  static class IsbnAdapter implements JsonbAdapter<Isbn, String> {
    @Override
    public String adaptToJson(Isbn obj) throws Exception {
      return obj.getValue();
    }

    @Override
    public Isbn adaptFromJson(String value) throws Exception {
      return Isbn.parse(value);
    }
  }

  @Generated("com.qualityminds.lazyval.processor.internal.codegen.JsonbGenerator")
  static class OrderDateAdapter implements JsonbAdapter<OrderDate, LocalDate> {
    @Override
    public LocalDate adaptToJson(OrderDate obj) throws Exception {
      return obj.value();
    }

    @Override
    public OrderDate adaptFromJson(LocalDate value) throws Exception {
      return new OrderDate(value);
    }
  }

  @Generated("com.qualityminds.lazyval.processor.internal.codegen.JsonbGenerator")
  static class QuantityAdapter implements JsonbAdapter<Quantity, Integer> {
    @Override
    public Integer adaptToJson(Quantity obj) throws Exception {
      return obj.value();
    }

    @Override
    public Quantity adaptFromJson(Integer value) throws Exception {
      return new Quantity(value);
    }
  }
}
