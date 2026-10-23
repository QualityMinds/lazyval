package scenarios.lettuce

import com.qualityminds.lazyval.LazyValue
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.UUID

// One domain-primitive per kind of payload the Lettuce generator encodes, so a single compiler run proves
// every branch of its text table produces code that compiles.

enum class Priority { LOW, HIGH }

@LazyValue
data class Flag(val value: Boolean)

@LazyValue
data class Counter(val value: Long)

@LazyValue
data class Price(val value: BigDecimal)

@LazyValue
data class Tracking(val value: UUID)

@LazyValue
data class Moment(val value: Instant)

@LazyValue
data class Zone(val value: ZoneId)

@LazyValue
data class Legacy(val value: Date)

@LazyValue
data class Level(val value: Priority)

@LazyValue
class Blob(val value: ByteArray)
