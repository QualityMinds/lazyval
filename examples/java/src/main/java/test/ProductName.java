package com.acme.sample;

// This file is intended to test the incremental compilation during CI.
// The CI will uncomment the contents forcing and trigger an additional compilation.
// In this case the SingleFileUtilsGenerator must not lose the other generated functions which will show up in the
// compilation of the "Test" class.


import de.qualityminds.lazyval.LazyValue;

@LazyValue
public record ProductName(String value) {

}
