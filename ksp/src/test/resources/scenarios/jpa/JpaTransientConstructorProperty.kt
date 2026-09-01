package scenarios.jpa

import com.qualityminds.lazyval.LazyValue
import jakarta.persistence.Transient

/**
 * Counterpart to `JpaTransientProperty`, but as a data class with the derived state declared in
 * the primary constructor — the Kotlin shape closest to a Java record with a `@Transient`
 * component. KSP attaches annotations written on a constructor `val` (without a use-site target)
 * to the `KSValueParameter`, not to the property declaration, so the validator has to consult the
 * parameter as well.
 */
@LazyValue
data class JpaTransientConstructorProperty(val value: String, @Transient val derivedLength: Int) {

    constructor(value: String) : this(value, value.length)
}
