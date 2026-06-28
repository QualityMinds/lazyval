package test.boundary.persistence.jpa

import jakarta.`annotation`.Generated
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.String
import scenarios.kotlin.Ids

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JpaGenerator")
@Converter(autoApply = true)
public class IdsProductIdAttributeConverter : AttributeConverter<Ids.ProductId?, String?> {
  override fun convertToDatabaseColumn(type: Ids.ProductId?): String? = type?.value

  override fun convertToEntityAttribute(dbValue: String?): Ids.ProductId? = dbValue?.let { Ids.ProductId.of(dbValue) }
}
