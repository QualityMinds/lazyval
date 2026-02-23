package com.qualityminds.lazyval.integration

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class EMail(val value: String){
    init {
        requireNotNull(value) { "EMail must not be null" }
    }
}
