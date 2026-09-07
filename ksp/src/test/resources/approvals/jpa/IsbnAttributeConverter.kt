package test.boundary.persistence.jpa

import jakarta.`annotation`.Generated
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import kotlin.String
import scenarios.kotlin.Isbn

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JpaGenerator")
@Converter(autoApply = true)
public class IsbnAttributeConverter : AttributeConverter<Isbn?, String?> {
  override fun convertToDatabaseColumn(type: Isbn?): String? = type?.value

  override fun convertToEntityAttribute(dbValue: String?): Isbn? = dbValue?.let { Isbn.parse(it) }
}
