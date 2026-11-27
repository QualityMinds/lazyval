package evaluation.k11

import com.qualityminds.lazyval.LazyvalConfiguration
import evaluation.k11.shared.Meters

/** Lists the library's Java record as an external domain-primitive. */
@LazyvalConfiguration(externalTypes = [Meters::class])
object MetersConfig
