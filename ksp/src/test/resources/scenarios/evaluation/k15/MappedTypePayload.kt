package evaluation.k15

import com.qualityminds.lazyval.LazyValue

/** Kotlin types that map to a Java type of another name: `kotlin.Any` is `java.lang.Object`. */
@LazyValue
data class Anything(val value: Any)

/** ...and `kotlin.CharSequence` is `java.lang.CharSequence`. */
@LazyValue
data class Text(val value: CharSequence)
