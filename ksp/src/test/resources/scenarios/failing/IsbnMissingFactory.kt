package scenarios.failing

import com.qualityminds.lazyval.LazyValue


@LazyValue
class IsbnMissingFactory private constructor(val value: String) {
    // fails because of private constructor
}