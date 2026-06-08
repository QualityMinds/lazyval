package com.qualityminds.lazyval.processor.internal.codegen;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.qualityminds.lazyval.processor.spi.Generator;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class GeneratedStamp {

    private static final ClassName GENERATED =
            ClassName.get("javax.annotation.processing", "Generated");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    public static AnnotationSpec forGenerator(Class<? extends Generator> clazz) {
        return AnnotationSpec.builder(GENERATED)
                .addMember("value", "$S", clazz.getName())
                .addMember("date", "$S", OffsetDateTime.now().format(FORMAT))
                .build();
    }

    private GeneratedStamp() {}
}
