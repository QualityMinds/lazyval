package test;

import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.ValueExtractor;
import scenarios.java.Isbn;

import javax.annotation.processing.Generated;

@Generated("com.qualityminds.lazyval.processor.internal.codegen.beanvalidation.BeanValidationGenerator")
@UnwrapByDefault
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
