package com.qualityminds.lazyval.ksp.internal.codegen

import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement

// Names for generated holders exposing one constant per domain-primitive, such as Spring Data's
// RedisSerializers or LazyvalLettuceCodecs.

private val WORD_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

/** `IdsProductId` → `IDS_PRODUCT_ID`, `EMail` → `E_MAIL`. */
internal fun constantName(element: ValidatedKspGeneratorElement): String =
    element.name.flatName().replace(WORD_BOUNDARY, "_").uppercase()

/**
 * Reports every constant two or more types would share — a holder with duplicate properties does not
 * compile — and returns whether all are unique.
 */
internal fun uniqueConstantNames(
    elements: Iterable<ValidatedKspGeneratorElement>,
    context: Generator.Context,
    generator: Generator,
): Boolean {
    val clashes = elements.groupBy { constantName(it) }.filterValues { it.size > 1 }.toSortedMap()
    clashes.forEach { (constant, clashing) ->
        context.logError(
            generator,
            "types ${clashing.map { it.name.canonicalName() }} would share the constant '$constant' in the generated code; rename one of them"
        )
    }
    return clashes.isEmpty()
}
