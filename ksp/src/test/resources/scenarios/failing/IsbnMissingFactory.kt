package scenarios.failing

import com.qualityminds.lazyval.LazyValue


/**
 * The `Isbn` shape without its companion factory: the payload is readable, but the only way back is a
 * private constructor. Generated code lives in another package, so the reconstruction call it emits
 * cannot resolve — the mistake has to be caught here rather than by kotlinc downstream.
 *
 * Kotlin counterpart to `ObjectWithPrivateConstructor.java`; contrast `PrivateFactoryClass`, where a
 * factory exists but is hidden too.
 */
@LazyValue
class IsbnMissingFactory private constructor(val value: String)