package scenarios.kotlin

import com.qualityminds.lazyval.LazyValue
import java.util.UUID
import util.IdGenerator

@LazyValue
class ProductId private constructor(val value: String) {
    companion object {
        @JvmStatic
        fun of(value: String): ProductId {
            require(value.isNotBlank()) { "ProductId cannot be blank" }
            return ProductId(value)
        }

        @JvmStatic
        fun createNew(): ProductId {
            return ProductId(UUID.randomUUID().toString())
        }

        @JvmStatic
        fun createNew(generator: IdGenerator): ProductId {
            return ProductId(generator.generateId())
        }
    }
}