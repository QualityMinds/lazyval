package evaluation.k5

import com.qualityminds.lazyval.LazyvalConfiguration
import evaluation.k5.shared.Celsius

/** Lists the library's Java type as an external domain-primitive. */
@LazyvalConfiguration(externalTypes = [Celsius::class])
object CelsiusConfig
