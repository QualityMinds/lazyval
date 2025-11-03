package de.qualityminds.lazyval.ksp.spi

import java.util.function.IntFunction

class NonEmptySet<out T> private constructor(
    private val head: T,
    private val tail: Set<T>
) : Set<T> by (tail + head) {

    companion object {
        fun <T> fromSet(set: Set<T>): NonEmptySet<T> {
            require(set.isNotEmpty()) { "Set must not be empty" }
            val iterator = set.iterator()
            val first = iterator.next()
            val rest = mutableSetOf<T>()
            iterator.forEachRemaining { rest.add(it) }
            return NonEmptySet(first, rest)
        }
    }

    val first: T get() = head

    override fun isEmpty(): Boolean = false
    override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?>? {
        return super.toArray(generator)
    }
}