package evaluation.k13

import com.qualityminds.lazyval.LazyValue

/** The domain-primitive of the first round, so that Lazyval runs its generators at all. */
@LazyValue
data class Seed(val value: String)
