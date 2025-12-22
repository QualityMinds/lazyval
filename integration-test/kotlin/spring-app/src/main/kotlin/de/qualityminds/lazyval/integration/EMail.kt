package de.qualityminds.lazyval.integration

import de.qualityminds.lazyval.LazyValue

@LazyValue
data class EMail(val value: String){
    init {
        requireNotNull(value) { "EMail must not be null" }
    }
}
