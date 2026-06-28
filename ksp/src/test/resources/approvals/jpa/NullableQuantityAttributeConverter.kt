package test.boundary.persistence.jpa

import jakarta.`annotation`.Generated
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.Int
import scenarios.kotlin.NullableQuantity

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JpaGenerator")
@Converter(autoApply = true)
public class NullableQuantityAttributeConverter : AttributeConverter<NullableQuantity?, Int?> {
  override fun convertToDatabaseColumn(type: NullableQuantity?): Int? = type?.value

  override fun convertToEntityAttribute(dbValue: Int?): NullableQuantity? = dbValue?.let { NullableQuantity.ofNullable(dbValue) }
}
