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
    /** ID of the stock generator for Bean Validation. */
    const val BEAN_VALIDATION = "beanvalidation"
    /** ID of the stock generator for Cassandra. */
    const val CASSANDRA_CODEC = "cassandra"
    /** ID of the stock generator for Jackson 2. */
    const val JACKSON_2 = "jackson-2"
    /** ID of the stock generator for Jackson 3. */
    const val JACKSON_3 = "jackson-3"
    /** ID of the stock generator for the Java-Persistence API */
    const val JPA = "jpa"
    /** ID of the stock generator for JSON-B. */
    const val JSONB = "jsonb"
    /** ID of the stock generator for Mapstruct. */
    const val MAPSTRUCT = "mapstruct"
    /** ID of the stock generator for Mongo-DB. */
    const val MONGODB_CODEC = "mongodb"
    /** ID of the stock generator for Spring-Data. */
    const val SPRING_DATA = "spring-data"
}
