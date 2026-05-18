package com.qualityminds.lazyval.processor.internal.codegen.beanvalidation;

import com.palantir.javapoet.JavaFile;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;

import java.util.stream.Stream;

class ResultHelper {

    static Stream<GeneratorResult> toResultStream(JavaFile javaFile, String packageName, String className) {
        var metadata = new GeneratorResult.Metadata(packageName, className);
        var javaResult = new GeneratorResult.Java(metadata, javaFile.toString());
        var serviceLoaderResult = new GeneratorResult.ServiceLoader(
                new GeneratorResult.Metadata("jakarta.validation", "ConstraintValidator"),
                metadata
        );
        return Stream.of(javaResult, serviceLoaderResult);
    }
}
