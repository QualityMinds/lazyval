package scenarios.failing

import com.qualityminds.lazyval.LazyValue

// `s` is public so the value-class rejection is the only problem this scenario has; a private
// property would additionally trip the accessibility rule and report two errors.
@LazyValue
@JvmInline
value class Password(val s: String)