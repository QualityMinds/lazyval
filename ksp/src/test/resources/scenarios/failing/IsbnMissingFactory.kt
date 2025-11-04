package scenarios.failing

import de.qualityminds.lazyval.LazyValue


@LazyValue
class IsbnMissingFactory private constructor(val value: String) {
    // fails because of private constructor
}