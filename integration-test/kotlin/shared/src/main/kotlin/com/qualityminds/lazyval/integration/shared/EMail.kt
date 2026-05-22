package com.qualityminds.lazyval.integration.shared

data class EMail(val value: String) {
    init {
        require(value.contains('@')) { "Invalid email format" }
    }
}