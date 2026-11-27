package evaluation.k14

import com.qualityminds.lazyval.LazyvalConfiguration
import evaluation.k14.shared.Code

/** Lists the library's Java type with a public field as an external domain-primitive. */
@LazyvalConfiguration(externalTypes = [Code::class])
object CodeConfig
