package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;

public sealed interface GeneratorResult {
    record Java(JavaFile fileSpec) implements GeneratorResult {}
    record Nothing() implements GeneratorResult {}
}
