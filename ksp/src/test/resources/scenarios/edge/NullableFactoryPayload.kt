package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `Int` payload rebuilt through a factory that takes `Int?`, which Lazyval matches since it ignores
 * nullability when matching. Java sees that parameter as `Integer`, so a boxed payload has to be passed as it
 * is: cast to `int`, javac would bind it to `of(Long)` instead, by widening.
 *
 * That overload declares a checked exception, so generated Java bound to it does not compile, which turns a
 * silent call to the wrong factory into an error a compile-only test can see.
 */
@LazyValue
class NullableFactoryPayload private constructor(val value: Int) {

    companion object {

        @JvmStatic
        fun of(value: Int?): NullableFactoryPayload = NullableFactoryPayload(value ?: 0)

        @JvmStatic
        @Throws(Exception::class)
        fun of(value: Long): NullableFactoryPayload = NullableFactoryPayload(value.toInt())
    }
}
