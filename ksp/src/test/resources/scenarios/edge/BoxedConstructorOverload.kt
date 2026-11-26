package scenarios.edge

import com.qualityminds.lazyval.LazyValue

/**
 * An `Int` payload rebuilt through its constructor, next to a second constructor taking `Int?` that Lazyval
 * leaves alone, as it matches a constructor by the payload's exact type. Java sees that one as taking
 * `Integer`, so a boxed payload has to be unboxed first: passed as it is, javac would bind it there.
 *
 * That constructor declares a checked exception, so generated Java bound to it does not compile, which turns a
 * silent call to the wrong constructor into an error a compile-only test can see.
 */
@LazyValue
class BoxedConstructorOverload(val value: Int) {

    @Throws(Exception::class)
    constructor(value: Int?) : this(value ?: 0)
}
