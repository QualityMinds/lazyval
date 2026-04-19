package com.acme.sample

import com.acme.sample.custom.toUpperCase2

fun main() {
    val email = EMail("a@b.de")
    val productId = ProductId("fubar")
    println("Testing generated Utils")
    println(email.toUpperCase2())
    println(productId.toUpperCase2())
}
