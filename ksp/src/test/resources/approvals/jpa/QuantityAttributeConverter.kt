package test.boundary.persistence.jpa

import jakarta.`annotation`.Generated
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.Int
import scenarios.kotlin.Quantity

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JpaGenerator")
@Converter(autoApply = true)
public class QuantityAttributeConverter : AttributeConverter<Quantity?, Int?> {
  override fun convertToDatabaseColumn(type: Quantity?): Int? = type?.value

  override fun convertToEntityAttribute(dbValue: Int?): Quantity? = dbValue?.let { Quantity(dbValue) }
}
