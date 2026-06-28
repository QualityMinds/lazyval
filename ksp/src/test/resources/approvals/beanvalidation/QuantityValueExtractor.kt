package test

import jakarta.`annotation`.Generated
import jakarta.validation.valueextraction.ValueExtractor
import scenarios.kotlin.Quantity

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation.BeanValidationGenerator")
public class QuantityValueExtractor : QuantityValueExtractorBase() {
  override fun extractValues(originalValue: Quantity?, `receiver`: ValueExtractor.ValueReceiver) {
    if (originalValue == null) {
      receiver.value(null, null)
      return
    }
    receiver.value(null, originalValue.value)
  }
}
