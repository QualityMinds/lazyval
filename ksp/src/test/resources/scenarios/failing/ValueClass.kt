package scenarios.failing

import com.qualityminds.lazyval.LazyValue

@LazyValue
@JvmInline
value class Password(private val s: String)