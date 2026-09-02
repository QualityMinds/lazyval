package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * The supported way to keep a payload property `internal`: hand Lazyval a `public` accessor function to
 * call instead. The property stays module-scoped, and exposing the value becomes something the author
 * did on purpose by writing the accessor rather than something Lazyval decided for them.
 *
 * This is what makes `failing/InternalPropertyClass` cost no capability, and the internal twin of
 * `ClassWithPrivatePropertyAndAccessor` — `rules.adoc` offers the accessor as the fix under
 * `[#visibility]` but only demonstrates it for `private`.
 */
@LazyValue
class InternalPropertyWithAccessor(internal val value: String) {

    fun value(): String = value
}
