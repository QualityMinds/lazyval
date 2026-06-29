package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import scenarios.java.OrderDate;

import javax.annotation.processing.Generated;
import java.time.LocalDate;

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
