package de.qualityminds.lazyval.integration;

import de.qualityminds.lazyval.LazyValue;

import static java.util.Objects.requireNonNull;

@LazyValue
public record EMail(String value) {

    public EMail {
        requireNonNull(value);
    }

}
