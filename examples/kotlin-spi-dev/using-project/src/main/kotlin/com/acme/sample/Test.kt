package com.acme.sample

import com.acme.sample.test.toUpperCase
import com.acme.sample.test.toUpperCase2

fun main() {
    val email = EMail("a@b.de")
    val productId = ProductId("fubar")

    println("Testing dedicated Utils")
    println(email.toUpperCase())
    println(productId.toUpperCase())
    println("Testing all-in-one Utils")
    println(email.toUpperCase2())
    println(productId.toUpperCase2())
}
