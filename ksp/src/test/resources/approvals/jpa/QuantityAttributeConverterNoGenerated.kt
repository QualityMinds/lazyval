package test.boundary.persistence.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.Int
import scenarios.kotlin.Quantity

@Converter(autoApply = true)
public class QuantityAttributeConverter : AttributeConverter<Quantity?, Int?> {
  override fun convertToDatabaseColumn(type: Quantity?): Int? = type?.value

  override fun convertToEntityAttribute(dbValue: Int?): Quantity? = dbValue?.let { Quantity(it) }
}
