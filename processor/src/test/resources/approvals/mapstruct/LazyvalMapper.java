package test;

import org.mapstruct.Mapper;
import scenarios.java.Ids;
import scenarios.java.Isbn;
import scenarios.java.OrderDate;
import scenarios.java.Quantity;

import javax.annotation.processing.Generated;
import java.time.LocalDate;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.MapstructGenerator")
@Mapper(
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR
)
public interface LazyvalMapper {
  default String mapIdsProductIdToString(Ids.ProductId type) {
    if(type == null) {
      return null;
    }
    return type.value();
  }

  default Ids.ProductId mapStringToIdsProductId(String value) {
    if(value == null) {
      return null;
    }
    return Ids.ProductId.of(value);
  }

  default String mapIsbnToString(Isbn type) {
    if(type == null) {
      return null;
    }
    return type.getValue();
  }

  default Isbn mapStringToIsbn(String value) {
    if(value == null) {
      return null;
    }
    return Isbn.parse(value);
  }

  default LocalDate mapOrderDateToLocalDate(OrderDate type) {
    if(type == null) {
      return null;
    }
    return type.value();
  }

  default OrderDate mapLocalDateToOrderDate(LocalDate value) {
    if(value == null) {
      return null;
    }
    return new OrderDate(value);
  }

  default int mapQuantityToInt(Quantity type) {
    return type.value();
  }

  default Quantity mapIntToQuantity(int value) {
    return new Quantity(value);
  }
}
