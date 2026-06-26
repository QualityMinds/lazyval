package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.qualityminds.lazyval.processor.spi.Generator;

public final class GeneratedStamp {

    private static final ClassName GENERATED =
            ClassName.get("javax.annotation.processing", "Generated");

    public static AnnotationSpec forGenerator(Class<? extends Generator> clazz) {
        return AnnotationSpec.builder(GENERATED)
                .addMember("value", "$S", clazz.getName())
                .build();
    }

    private GeneratedStamp() {}
}
