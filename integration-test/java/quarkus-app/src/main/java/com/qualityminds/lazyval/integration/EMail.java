package com.qualityminds.lazyval.integration;

import com.qualityminds.lazyval.LazyValue;

import static java.util.Objects.requireNonNull;

@LazyValue
public record EMail(String value) {

    public EMail {
        requireNonNull(value);
    }

}
