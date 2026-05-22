package scenarios

import com.qualityminds.lazyval.LazyvalConfiguration
import java.util.Optional

@LazyvalConfiguration(externalTypes = [Optional::class])
object ConfigWithExternal