package scenarios.duplicateexternal

import com.qualityminds.lazyval.LazyvalConfiguration
import java.time.Year

@LazyvalConfiguration(externalTypes = [Year::class, Year::class])
object Config
