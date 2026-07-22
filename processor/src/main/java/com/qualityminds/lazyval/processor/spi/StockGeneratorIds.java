package com.qualityminds.lazyval.processor.spi;

import org.jetbrains.annotations.ApiStatus;

/**
 * Public identifiers of the stock generators shipped with Lazyval. Provided so that third-party
 * {@link Generator} implementations can safely reference stock generators — for example when
 * declaring {@link Generator#supersedes()} — without hardcoding string literals.
 * <p>
 * These strings are the same values users pass to {@code lazyval.generators.disable=<id>} in their
 * build configuration. Renaming a value here is a breaking API change.
 */
@ApiStatus.Experimental
public final class StockGeneratorIds {
    /** ID of the stock generator for Bean Validation. */
    public static final String BEAN_VALIDATION = "beanvalidation";
    /** ID of the stock generator for Cassandra. */
    public static final String CASSANDRA_CODEC = "cassandra";
    /** ID of the stock generator for Jackson 2. */
    public static final String JACKSON_2 = "jackson-2";
    /** ID of the stock generator for Jackson 3. */
    public static final String JACKSON_3 = "jackson-3";
    /** ID of the stock generator for the Java-Persistence API */
    public static final String JPA = "jpa";
    /** ID of the stock generator for JSON-B. */
    public static final String JSONB = "jsonb";
    /** ID of the stock generator for Mapstruct. */
    public static final String MAPSTRUCT = "mapstruct";
    /** ID of the stock generator for Mongo-DB. */
    public static final String MONGODB_CODEC = "mongodb";
    /** ID of the stock generator for Spring-Data. */
    public static final String SPRING_DATA = "spring-data";

    private StockGeneratorIds() {}
}
