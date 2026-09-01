package scenarios.springdata

import com.qualityminds.lazyval.LazyValue
import org.springframework.data.annotation.Transient

/**
 * Counterpart to `SpringDataTransientProperty`, but as a data class with the derived state
 * declared in the primary constructor. KSP attaches annotations written on a constructor `val`
 * (without a use-site target) to the `KSValueParameter`, not to the property declaration, so the
 * validator has to consult the parameter as well.
 */
@LazyValue
data class SpringDataTransientConstructorProperty(val value: String, @Transient val derivedLength: Int) {

    constructor(value: String) : this(value, value.length)
}
