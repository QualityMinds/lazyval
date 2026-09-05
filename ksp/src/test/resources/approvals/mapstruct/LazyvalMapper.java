package test;

import jakarta.annotation.Generated;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import scenarios.kotlin.Ids;
import scenarios.kotlin.Isbn;
import scenarios.kotlin.NullableQuantity;
import scenarios.kotlin.OrderDate;
import scenarios.kotlin.Quantity;

import java.time.LocalDate;

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default String mapIdsProductIdToString(Ids.ProductId type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default Ids.ProductId mapStringToIdsProductId(String value) {
    if (value == null) {
      return null;
    }
    return Ids.ProductId.of(value);
  }

  default String mapIsbnToString(Isbn type) {
    if (type == null) {
      return null;
    }
    return type.getValue();
  }

  default Isbn mapStringToIsbn(String value) {
    if (value == null) {
      return null;
    }
    return Isbn.parse(value);
  }

  default int mapNullableQuantityToInt(NullableQuantity type) {
    return type.getValue();
  }

  default NullableQuantity mapIntToNullableQuantity(int value) {
    return NullableQuantity.ofNullable(value);
  }

  default LocalDate mapOrderDateToLocalDate(OrderDate type) {
    if (type == null) {
      return null;
    }
    return type.getDate();
  }

  default OrderDate mapLocalDateToOrderDate(LocalDate value) {
    if (value == null) {
      return null;
    }
    return new OrderDate(value);
  }

  default int mapQuantityToInt(Quantity type) {
    return type.getValue();
  }

  default Quantity mapIntToQuantity(int value) {
    return new Quantity(value);
  }
}
