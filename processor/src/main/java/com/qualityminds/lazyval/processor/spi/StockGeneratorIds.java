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

    public static final String BEAN_VALIDATION = "beanvalidation";
    public static final String CASSANDRA_CODEC = "cassandra";
    public static final String JACKSON_2 = "jackson-2";
    public static final String JACKSON_3 = "jackson-3";
    public static final String JPA = "jpa";
    public static final String JSONB = "jsonb";
    public static final String MAPSTRUCT = "mapstruct";
    public static final String MONGODB_CODEC = "mongodb";
    public static final String SPRING_DATA = "spring-data";

    private StockGeneratorIds() {}
}
