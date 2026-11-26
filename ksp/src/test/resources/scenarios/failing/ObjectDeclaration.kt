package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * An object is its own only instance, so there is nothing to rebuild from a payload. Refused before any other
 * rule is asked, which is why the property alone does not make it a domain-primitive.
 */
@LazyValue
object ObjectDeclaration {
    val value: String = "constant"
}
