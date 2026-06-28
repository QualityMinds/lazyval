package test.boundary.persistence.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import scenarios.java.Isbn;

import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.JpaGenerator")
@Converter(
    autoApply = true
)
public class IsbnAttributeConverter implements AttributeConverter<Isbn, String> {
  public String convertToDatabaseColumn(Isbn type) {
    if(type == null) {
      return null;
    }
    return type.getValue();
  }

  public Isbn convertToEntityAttribute(String dbValue) {
    if(dbValue == null) {
      return null;
    }
    return Isbn.parse(dbValue);
  }
}
