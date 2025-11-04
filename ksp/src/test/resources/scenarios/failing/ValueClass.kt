package scenarios.failing

import de.qualityminds.lazyval.LazyValue

@LazyValue
@JvmInline
value class Password(private val s: String)