package test.domain

import jakarta.`annotation`.Generated
import jakarta.validation.valueextraction.ValueExtractor
import jakarta.validation.valueextraction.UnwrapByDefault
import scenarios.kotlin.OrderDate

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation.BeanValidationGenerator")
@UnwrapByDefault
public class OrderDateValueExtractor : OrderDateValueExtractorBase() {
  override fun extractValues(originalValue: OrderDate?, `receiver`: ValueExtractor.ValueReceiver) {
    if (originalValue == null) {
      receiver.value(null, null)
      return
    }
    receiver.value(null, originalValue.date)
  }
}
