package test

import de.qualityminds.lazyval.LazyValue

@LazyValue
class MultipleFactoriesClass private constructor(val value: String) {

    companion object {

        fun ofNullable(value: String?): MultipleFactoriesClass?{
            return value?.let{
                MultipleFactoriesClass(value)
            }
        }

        fun of(value: String): MultipleFactoriesClass {
            MultipleFactoriesClass(value)
        }
    }
}