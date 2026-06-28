package test.boundary.persistence.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import scenarios.java.OrderDate;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.JpaGenerator")
@Converter(
    autoApply = true
)
public class OrderDateAttributeConverter implements AttributeConverter<OrderDate, LocalDate> {
  public LocalDate convertToDatabaseColumn(OrderDate type) {
    if(type == null) {
      return null;
    }
    return type.value();
  }

  public OrderDate convertToEntityAttribute(LocalDate dbValue) {
    if(dbValue == null) {
      return null;
    }
    return new OrderDate(dbValue);
  }
}
