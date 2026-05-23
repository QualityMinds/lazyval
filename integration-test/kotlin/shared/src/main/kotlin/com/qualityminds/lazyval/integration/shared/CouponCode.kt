package com.qualityminds.lazyval.integration.shared

class CouponCode private constructor(val value: String) {

    companion object {
        @JvmStatic
        fun ofNullable(value: String?): CouponCode? {
            if (value.isNullOrBlank()) return null
            return CouponCode(value.trim().uppercase())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val couponCode = other as CouponCode
        return value == couponCode.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CouponCode{$value}"
}
