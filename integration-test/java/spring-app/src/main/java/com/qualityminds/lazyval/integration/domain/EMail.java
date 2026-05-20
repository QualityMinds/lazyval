package com.qualityminds.lazyval.integration.domain;

import com.qualityminds.lazyval.LazyValue;

import static java.util.Objects.requireNonNull;

@LazyValue
public record EMail(String value) {

    public EMail {
        requireNonNull(value, "EMail must not be null");
    }
}
