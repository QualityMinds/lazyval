package com.qualityminds.lazyval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Project-level configuration for the Lazyval annotation processor.
 * <p>
 * <b>Java projects:</b> place this annotation on a single {@code package-info.java}
 * per compilation unit (typically the top-level package of the module).
 * <pre>{@code
 * @LazyvalConfiguration(externalTypes = {Foo.class, Bar.class})
 * package com.qualityminds.lazyval.integration;
 * }
 * </pre>
 * <p>
 * <b>Kotlin projects:</b> KSP does not expose annotations on Java
 * {@code package-info.java}, and Kotlin has no package-level annotation site.
 * Place this annotation on a single, dedicated marker object instead, e.g.
 * <pre>{@code
 * @LazyvalConfiguration(externalTypes = [Foo::class, Bar::class])
 * object LazyvalConfig
 * }</pre>
 * <p>
 * Presence of this annotation triggers the processor in the same way {@link LazyValue}
 * does, so a module that has no {@code @LazyValue}-annotated types of its own can
 * still drive generation purely from {@link #externalTypes()}.
 * <p>
 * <b>Errors raised by the processor:</b>
 * <ul>
 *   <li>More than one {@code @LazyvalConfiguration} annotation present in
 *       the compilation unit — only one is allowed; the extra one is
 *       reported as a compiler error.</li>
 *   <li>A listed type in {@link #externalTypes()} belongs to the current compilation unit — reported as
 *       a compiler error pointing at the configuration, with the
 *       suggestion to annotate the type with {@link LazyValue} instead.</li>
 * </ul>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface LazyvalConfiguration {

    /**
     * Value types from outside the current compilation-unit (typically from a
     * dependency JAR) for which Lazyval should still run generations.
     * <p>
     * Use this only for types defined outside the current compilation unit.
     *
     * @return external value types to include in this module's Lazyval processing
     */
    Class<?>[] externalTypes() default {};
}
