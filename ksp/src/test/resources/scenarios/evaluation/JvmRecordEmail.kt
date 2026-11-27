package scenarios.evaluation

import com.qualityminds.lazyval.LazyValue

/**
 * A domain-primitive declared as a JVM record, which kotlinc accepts only with `-jvm-target` 16 or later.
 */
@LazyValue
@JvmRecord
data class JvmRecordEmail(val value: String)
