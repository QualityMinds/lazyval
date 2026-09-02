package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` factory with the annotation left off. `internal` itself is fine — see
 * `edge/InternalFactoryWithJvmName` — but without `@JvmName` Kotlin compiles this to `of$module`, and
 * the suffix is a module name that KSP and kotlinc are each told separately by the build. Lazyval will
 * not hard-code a name it cannot verify, so it asks for the annotation instead.
 *
 * Contrast `edge/InternalConstructorClass`: a constructor is `<init>` in the bytecode and has no name to
 * mangle, so `internal` there needs nothing added.
 */
@LazyValue
class InternalFactoryClass private constructor(val value: String) {

    companion object {

        @JvmStatic
        internal fun of(value: String): InternalFactoryClass = InternalFactoryClass(value)
    }
}
