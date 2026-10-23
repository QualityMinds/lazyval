package scenarios.lettuce

import com.qualityminds.lazyval.LazyValue
import java.util.Locale

/** Wraps a payload type the Lettuce generator has no text encoding for. */
@LazyValue
data class LanguageTag(val value: Locale)
