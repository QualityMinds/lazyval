package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import java.lang.Override;
import java.time.LocalDate;
import javax.annotation.processing.Generated;
import scenarios.java.OrderDate;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.beanvalidation.BeanValidationGenerator")
public class OrderDateValueExtractor implements ValueExtractor<@ExtractedValue(type = LocalDate.class) OrderDate> {
  @Override
  public void extractValues(OrderDate originalValue, ValueExtractor.ValueReceiver receiver) {
    if (originalValue == null) {
      receiver.value(null, null);
      return;
    }
    receiver.value(null, originalValue.value());
  }
}
