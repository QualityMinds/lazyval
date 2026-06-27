package test.boundary.persistence.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import scenarios.java.Quantity;

import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.JpaGenerator")
@Converter(
        autoApply = true
)
public class QuantityAttributeConverter implements AttributeConverter<Quantity, Integer> {
    public Integer convertToDatabaseColumn(Quantity type) {
        if(type == null) {
            return null;
        }
        return type.value();
    }

    public Quantity convertToEntityAttribute(Integer dbValue) {
        if(dbValue == null) {
            return null;
        }
        return new Quantity(dbValue);
    }
}
