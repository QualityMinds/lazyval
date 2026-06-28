package test

import jakarta.`annotation`.Generated
import jakarta.validation.valueextraction.ValueExtractor
import scenarios.kotlin.Isbn

@Generated("com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation.BeanValidationGenerator")
public class IsbnValueExtractor : IsbnValueExtractorBase() {
  override fun extractValues(originalValue: Isbn?, `receiver`: ValueExtractor.ValueReceiver) {
    if (originalValue == null) {
      receiver.value(null, null)
      return
    }
    receiver.value(null, originalValue.value)
  }
}
