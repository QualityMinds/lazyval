package scenarios.failing

import com.qualityminds.lazyval.LazyValue
import kotlin.time.Duration

/**
 * `Duration` is a value class hiding its `rawValue`, exactly as `ValueClassPrivateProperty` does — but
 * it is the standard library's declaration, so "make the property public" is advice about a file the
 * author cannot open. The diagnostic says so instead.
 *
 * Lazyval will not pick a unit on the author's behalf either: `inWholeNanoseconds` saturates past
 * roughly 292 years and `inWholeSeconds` throws precision away, and neither choice is Lazyval's.
 */
@LazyValue
class ValueClassForeignProperty(val timeout: Duration)
