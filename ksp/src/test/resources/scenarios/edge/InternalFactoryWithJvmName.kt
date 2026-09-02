package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `internal` factory, supported: construction is restricted to the declaring module, the payload
 * still reads through a public accessor, and nothing the author hid gets published — which is what
 * separates this from an internal payload property.
 *
 * `@JvmName` is what makes it work, and Lazyval requires it rather than emitting the mangled
 * `of$module` itself. That name is callable from Java, but its suffix comes from the build, and KSP and
 * kotlinc are told it separately; an annotation in the source is something the processor can verify.
 *
 * The counterpart to `InternalConstructorClass`, which needs no annotation because a constructor has no
 * name to mangle.
 */
@LazyValue
class InternalFactoryWithJvmName private constructor(val value: String) {

    companion object {

        @JvmStatic
        @JvmName("of")
        internal fun of(value: String): InternalFactoryWithJvmName = InternalFactoryWithJvmName(value)
    }
}
