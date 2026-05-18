package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.squareup.kotlinpoet.FileSpec
import java.util.stream.Stream

internal object ResultHelper {

    fun toResultStream(fileSpec: FileSpec, packageName: String, className: String): Stream<GeneratorResult> {
        val metadata = GeneratorResult.Metadata(packageName, className)
        val kotlinResult = GeneratorResult.Kotlin(metadata, fileSpec.toString())
        val serviceLoaderResult = GeneratorResult.ServiceLoader(
            GeneratorResult.Metadata("jakarta.validation", "ConstraintValidator"),
            metadata
        )
        return Stream.of(kotlinResult, serviceLoaderResult)
    }
}
