package test

import jakarta.`annotation`.Generated
import jakarta.validation.valueextraction.ValueExtractor
import scenarios.kotlin.Ids

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation.BeanValidationGenerator")
public class IdsProductIdValueExtractor : IdsProductIdValueExtractorBase() {
  override fun extractValues(originalValue: Ids.ProductId?, `receiver`: ValueExtractor.ValueReceiver) {
    if (originalValue == null) {
      receiver.value(null, null)
      return
    }
    receiver.value(null, originalValue.value)
  }
}
