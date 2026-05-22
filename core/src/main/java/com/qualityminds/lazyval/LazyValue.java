package com.qualityminds.lazyval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a value-type/domain-primitive for the Lazyval processing.
 * <p>
 * The following requirements are applied to be considered valid for lazyval:
 * <ul>
 *     <li>must only wrap a single value — exactly one non-transient instance field is the storage payload.
 *         Derived state computable from that payload may be kept in additional fields, but they must be marked
 *         {@code transient} (Java) or {@code @kotlin.jvm.Transient} (Kotlin) so the processor ignores them.
 *      <ul><li>Kotlin: wrapped value must not be nullable</li></ul>
 *     </li>
 *     <li>must not be abstract</li>
 *     <li>can provide a factory method, which takes precedence for object creation. Additional constructors,
 *         accessors, or convenience factories with other signatures are allowed and ignored by the processor.</li>
 *     <li>class and field should be final</li>
 * </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface LazyValue {
}
