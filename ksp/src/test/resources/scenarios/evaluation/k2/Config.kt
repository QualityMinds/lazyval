package evaluation.k2

import com.qualityminds.lazyval.LazyvalConfiguration
import java.time.Year

/** Holder of the external types: no generated file names it as a source, which is what the finding is about. */
@LazyvalConfiguration(externalTypes = [Year::class])
object Config
