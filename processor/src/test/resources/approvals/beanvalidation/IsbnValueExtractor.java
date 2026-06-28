package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import java.lang.Override;
import java.lang.String;
import javax.annotation.processing.Generated;
import scenarios.java.Isbn;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.beanvalidation.BeanValidationGenerator")
public class IsbnValueExtractor implements ValueExtractor<@ExtractedValue(type = String.class) Isbn> {
  @Override
  public void extractValues(Isbn originalValue, ValueExtractor.ValueReceiver receiver) {
    if (originalValue == null) {
      receiver.value(null, null);
      return;
    }
    receiver.value(null, originalValue.getValue());
  }
}
