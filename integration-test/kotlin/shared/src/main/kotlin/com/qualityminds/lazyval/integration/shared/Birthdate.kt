package com.qualityminds.lazyval.integration.shared

import java.time.LocalDate
import java.util.regex.Pattern

class Birthdate private constructor(parsedDate: ParsedDate) : Comparable<Birthdate> {

    /**
     * ISO-8601 representation
     */
    val value: String = parsedDate.asIsoString()

    @Transient
    val state: State = when {
        parsedDate.isComplete -> State.Complete(LocalDate.of(parsedDate.year, parsedDate.month, parsedDate.day))
        parsedDate.isDayUnknown -> State.DayUnknown(parsedDate.year, parsedDate.month)
        parsedDate.isDayMonthUnknown -> State.DayMonthUnknown(parsedDate.year)
        else -> State.Unknown
    }

    override fun compareTo(other: Birthdate): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        return value == (other as Birthdate).value
    }

    override fun hashCode(): Int = value.hashCode()

    sealed interface State {
        data class Complete(val date: LocalDate) : State
        data class DayUnknown(val month: Int, val year: Int) : State
        data class DayMonthUnknown(val year: Int) : State
        data object Unknown : State
    }

    private data class ParsedDate(val year: Int, val month: Int, val day: Int) {

        init {
            checkValid()
        }

        val isUnknown: Boolean get() = year == 0 && month == 0 && day == 0
        val isComplete: Boolean get() = year != 0 && month != 0 && day != 0
        val isDayUnknown: Boolean get() = year == 0 && month == 0 && day != 0
        val isDayMonthUnknown: Boolean get() = year != 0 && month == 0 && day == 0

        /**
         * will cause a DateTimeParseException for invalid Dates
         */
        private fun checkValid() {
            LocalDate.of(
                year,
                if (month != 0) month else 1,
                if (day != 0) day else 1,
            )
        }

        /**
         * Converts the parsingresult to yyyy-mm-dd
         */
        fun asIsoString(): String = "%04d-%02d-%02d".format(year, month, day)

        companion object {
            fun from(date: LocalDate): ParsedDate =
                ParsedDate(date.year, date.monthValue, date.dayOfMonth)
        }
    }

    private object Parser {

        private val DATE_PATTERN_ISO_8601: Pattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")

        fun parse(value: String): ParsedDate {
            if (!DATE_PATTERN_ISO_8601.matcher(value).matches()) {
                throw IllegalArgumentException("Date '$value' is not matching yyyy-mm-dd")
            }
            return parseIso(value)
        }

        private fun parseIso(value: String): ParsedDate = ParsedDate(
            value.substring(0, 4).toInt(),
            value.substring(5, 7).toInt(),
            value.substring(8, 10).toInt(),
        )
    }

    companion object {
        @JvmField
        val UNKNOWN: Birthdate = Birthdate(ParsedDate(0, 0, 0))

        @JvmStatic
        fun of(value: String?): Birthdate? {
            if (value.isNullOrBlank()) return null
            val parsedDate = Parser.parse(value)
            // use shared instance
            return if (parsedDate.isUnknown) UNKNOWN else Birthdate(parsedDate)
        }

        @JvmStatic
        fun of(date: LocalDate?): Birthdate? {
            if (date == null) return null
            return Birthdate(ParsedDate.from(date))
        }
    }
}
