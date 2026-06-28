package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import java.lang.Integer;
import java.lang.Override;
import javax.annotation.processing.Generated;
import scenarios.java.Quantity;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.beanvalidation.BeanValidationGenerator")
public class QuantityValueExtractor implements ValueExtractor<@ExtractedValue(type = Integer.class) Quantity> {
  @Override
  public void extractValues(Quantity originalValue, ValueExtractor.ValueReceiver receiver) {
    if (originalValue == null) {
      receiver.value(null, null);
      return;
    }
    receiver.value(null, originalValue.value());
  }
}
