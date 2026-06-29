package test;

import jakarta.annotation.Generated;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import scenarios.kotlin.Quantity;

/**
 * Abstract base class providing the {@code ValueExtractor} superinterface
 * declaration for {@code Quantity}.
 *
 * <p>This class must be written in Java. The Kotlin compiler does not emit
 * {@code RuntimeVisibleTypeAnnotations} for type-use annotations on generic supertype
 * arguments
 * (see <a href="https://youtrack.jetbrains.com/issue/KT-19289">KT-19289</a>).
 * Jakarta Bean Validation providers such as Hibernate Validator discover
 * {@code @ExtractedValue} by calling {@link Class#getAnnotatedInterfaces()} at runtime,
 * which relies on that JVM bytecode attribute. Placing the superinterface declaration in
 * Java source ensures {@code javac} emits the required attribute, making the extractor
 * discoverable by any compliant Bean Validation provider. The concrete implementation is
 * provided by {@code QuantityValueExtractor}.
 */
@Generated("com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation.BeanValidationGenerator")
public abstract class QuantityValueExtractorBase implements ValueExtractor<@ExtractedValue(type = Integer.class) Quantity> {
}
