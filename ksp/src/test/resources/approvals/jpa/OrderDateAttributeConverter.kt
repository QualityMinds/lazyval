package test.boundary.persistence.jpa

import jakarta.`annotation`.Generated
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.LocalDate
import scenarios.kotlin.OrderDate

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.JpaGenerator")
@Converter(autoApply = true)
public class OrderDateAttributeConverter : AttributeConverter<OrderDate?, LocalDate?> {
  override fun convertToDatabaseColumn(type: OrderDate?): LocalDate? = type?.date

  override fun convertToEntityAttribute(dbValue: LocalDate?): OrderDate? = dbValue?.let { OrderDate(dbValue) }
}
