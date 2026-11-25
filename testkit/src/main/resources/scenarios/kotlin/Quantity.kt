package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue
import java.util.Optional

@LazyValue
data class Quantity(val value: Int) {
    init {
        require(value >= 0) { "Quantity must be greater than 0" }
    }

    companion object {

        @JvmStatic
        fun of(value: Int): Quantity = Quantity(value)

        @JvmStatic
        fun of(nullable: Int?): Optional<Quantity> = Optional.ofNullable(nullable).map { of(it) }
    }
}
