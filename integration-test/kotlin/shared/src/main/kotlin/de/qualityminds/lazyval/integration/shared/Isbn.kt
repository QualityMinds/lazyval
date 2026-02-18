package de.qualityminds.lazyval.integration.shared

class Isbn private constructor(val value: String) {

    companion object {
        // will be used by the annotation processor (factory methods have higher precedence)
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun parse(value: String): Isbn {
            requireNotNull(value) { "ISBN cannot be null" }

            val cleanValue = value.replace(Regex("[-\\s]"), "")

            when (cleanValue.length) {
                10 -> validateIsbn10(cleanValue)
                13 -> validateIsbn13(cleanValue)
                else -> throw IllegalArgumentException("Invalid ISBN length. Must be 10 or 13 digits (excluding hyphens)")
            }

            return Isbn(value)
        }

        private fun validateIsbn10(isbn: String) {
            var sum = 0

            for (i in 0..8) {
                val c = isbn[i]
                if (!c.isDigit()) {
                    throw IllegalArgumentException("Invalid ISBN-10 format: contains non-digit characters")
                }
                sum += c.digitToInt() * (10 - i)
            }

            val lastChar = isbn[9]
            val checkDigit = when {
                lastChar == 'X' || lastChar == 'x' -> 10
                lastChar.isDigit() -> lastChar.digitToInt()
                else -> throw IllegalArgumentException("Invalid ISBN-10 format: last character must be digit or X")
            }
            sum += checkDigit

            if (sum % 11 != 0) {
                throw IllegalArgumentException("Invalid ISBN-10: checksum validation failed")
            }
        }

        private fun validateIsbn13(isbn: String) {
            var sum = 0

            for (i in 0..11) {
                val c = isbn[i]
                if (!c.isDigit()) {
                    throw IllegalArgumentException("Invalid ISBN-13 format: contains non-digit characters")
                }
                val digit = c.digitToInt()
                sum += if (i % 2 == 0) digit else digit * 3
            }

            val lastChar = isbn[12]
            if (!lastChar.isDigit()) {
                throw IllegalArgumentException("Invalid ISBN-13 format: contains non-digit characters")
            }
            val checkDigit = lastChar.digitToInt()
            val calculatedCheckDigit = (10 - (sum % 10)) % 10

            if (checkDigit != calculatedCheckDigit) {
                throw IllegalArgumentException("Invalid ISBN-13: checksum validation failed")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val isbn = other as Isbn
        return value == isbn.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "ISBN{$value}"
    }
}