package com.acme.sample;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public record ProductId(String value) {
    // usually this would have some regex, max length, etc.
    // we just want a second type here for testing
}
