package com.acme.sample

import com.qualityminds.lazyval.LazyValue

@LazyValue
data class EMail(val value: String) {
    companion object {
        // a very simple email regex (don't use this)
        private val REGEX: Regex = "^(.+)@(\\S+)$".toRegex()
    }

    init {
        require(value.length <= 254) { "EMail must exceed 254 characters!" }
        require(REGEX.matches(value)) { "Invalid EMail format!" }
    }
}

@LazyValue
data class ProductId(val value: String)


@LazyValue
data class Quantity(val value: Int)