package test.boundary.persistence.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import scenarios.java.Ids;

import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.JpaGenerator")
@Converter(
    autoApply = true
)
public class IdsProductIdAttributeConverter implements AttributeConverter<Ids.ProductId, String> {
  public String convertToDatabaseColumn(Ids.ProductId type) {
    if(type == null) {
      return null;
    }
    return type.value();
  }

  public Ids.ProductId convertToEntityAttribute(String dbValue) {
    if(dbValue == null) {
      return null;
    }
    return Ids.ProductId.of(dbValue);
  }
}
