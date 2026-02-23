package scenarios.failing

import com.qualityminds.lazyval.LazyValue

/**
 * A nullable wrapped type is illegal, because you would rather mark the wrapper nullable (not like JPA Embeddables,
 * which give you a reference to a class with all fields being null)
 */
@LazyValue
class NullableWrappedType (val value: String?) {}