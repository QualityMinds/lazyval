package scenarios.edge

import com.qualityminds.lazyval.LazyValue

@LazyValue
class ClassWithTransientProperty private constructor(val value: String) {

    @Transient
    val derivedYear: Int = value.substring(0, 4).toInt()

    companion object {
        @JvmStatic
        fun of(value: String): ClassWithTransientProperty {
            require(value.isNotBlank()) { "value cannot be blank" }
            return ClassWithTransientProperty(value)
        }
    }
}