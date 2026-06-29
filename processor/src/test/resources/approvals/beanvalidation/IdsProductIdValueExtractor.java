package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import scenarios.java.Ids;

import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.beanvalidation.BeanValidationGenerator")
public class IdsProductIdValueExtractor implements ValueExtractor<Ids. @ExtractedValue(type = String.class) ProductId> {
  @Override
  public void extractValues(Ids.ProductId originalValue, ValueExtractor.ValueReceiver receiver) {
    if (originalValue == null) {
      receiver.value(null, null);
      return;
    }
    receiver.value(null, originalValue.value());
  }
}
