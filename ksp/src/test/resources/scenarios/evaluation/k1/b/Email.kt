package evaluation.k1.b

import com.qualityminds.lazyval.LazyValue

/** The second `Email`, in another package. It validates where the first does not, so losing either output matters. */
@LazyValue
data class Email(val value: String) {
    init {
        require(value.contains('@')) { "an e-mail address needs an '@'" }
    }
}
