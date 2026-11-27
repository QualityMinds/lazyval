package evaluation.k23

import com.qualityminds.lazyval.LazyvalConfiguration

/**
 * Lists a nested type of this compilation unit as external, which `failing/LocalTypeAsExternal.kt` shows is an
 * error for a top-level type: such a type belongs on `@LazyValue`.
 */
@LazyvalConfiguration(externalTypes = [Holder.Nested::class])
object NestedLocalTypeAsExternal

object Holder {
    data class Nested(val value: String)
}
