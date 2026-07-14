package com.qualityminds.lazyval.ksp.spi

import org.jetbrains.annotations.ApiStatus

/**
 * Public identifiers of the stock generators shipped with Lazyval. Provided so that third-party
 * [Generator] implementations can safely reference stock generators — for example when declaring
 * [Generator.supersedes] — without hardcoding string literals.
 *
 * These strings are the same values users pass to `lazyval.generators.disable=<id>` in their build
 * configuration. Renaming a value here is a breaking API change.
 */
@ApiStatus.Experimental
object StockGeneratorIds {

    const val BEAN_VALIDATION = "beanvalidation"
    const val CASSANDRA_CODEC = "cassandra"
    const val JACKSON_2 = "jackson-2"
    const val JACKSON_3 = "jackson-3"
    const val JPA = "jpa"
    const val JSONB = "jsonb"
    const val MAPSTRUCT = "mapstruct"
    const val MONGODB_CODEC = "mongodb"
    const val SPRING_DATA = "spring-data"
}
