package com.qualityminds.lazyval.integration.shared;

import static java.util.Objects.requireNonNull;

public record EMail(String value) {

    public EMail {
        requireNonNull(value);
    }

}
