package de.qualityminds.lazyval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a value-type/domain-primitive for the Lazyval processing.
 * <p>
 * The following requirements are applied to be considered valid for lazyval:
 * <ul>
 *     <li>must only wrap a single value</li>
 *     <ul><li>Kotlin: wrapped value must not be nullable</li></ul>
 *     <li>must not be abstract</li>
 *     <li>can provide a factory method, which takes precedence for object creation</li>
 *     <li>class and field should be final</li>
 * </ul>
 * </p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface LazyValue {
}
