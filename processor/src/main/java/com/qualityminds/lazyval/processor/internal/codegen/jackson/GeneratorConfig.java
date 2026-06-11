package com.qualityminds.lazyval.processor.internal.codegen.jackson;

import com.palantir.javapoet.ClassName;
import com.qualityminds.lazyval.processor.spi.Generator;

/**
 * Captures the package/class differences between Jackson 2 and Jackson 3.
 */
record GeneratorConfig(
        String spiPackage,
        String spiClass,
        String requiredClasspath,
        String corePackage,
        String databindPackage,
        String serStdPackage,
        String deserStdPackage,
        String modulePackage,
        String serializerProviderClass,
        String deserializationContextClass,
        String setupContextOuterClass,
        String objectMapperPackage,
        String lazyvalJacksonModuleName,
        String valueSerializerClass,
        String valueDeserializerClass,
        boolean deserializerDeclaresExceptions,
        Class<? extends Generator> executingGenerator
) {
    static final GeneratorConfig JACKSON_2 = new GeneratorConfig(
            "com.fasterxml.jackson.databind",
            "Module",
            "com.fasterxml.jackson.databind.Module",
            "com.fasterxml.jackson.core",
            "com.fasterxml.jackson.databind",
            "com.fasterxml.jackson.databind.ser.std",
            "com.fasterxml.jackson.databind.deser.std",
            "com.fasterxml.jackson.databind.module",
            "SerializerProvider",
            "DeserializationContext",
            "Module",
            "com.fasterxml.jackson.databind",
            "LazyvalJackson2Module",
            "JsonSerializer",
            "JsonDeserializer",
            true,
            Jackson2Generator.class
    );

    static final GeneratorConfig JACKSON_3 = new GeneratorConfig(
            "tools.jackson.databind",
            "JacksonModule",
            "tools.jackson.databind.JacksonModule",
            "tools.jackson.core",
            "tools.jackson.databind",
            "tools.jackson.databind.ser.std",
            "tools.jackson.databind.deser.std",
            "tools.jackson.databind.module",
            "SerializationContext",
            "DeserializationContext",
            "JacksonModule",
            "tools.jackson.databind",
            "LazyvalJacksonModule",
            "ValueSerializer",
            "ValueDeserializer",
            false,
            Jackson3Generator.class
    );

    ClassName jsonGenerator() {
        return ClassName.get(corePackage, "JsonGenerator");
    }

    ClassName jsonParser() {
        return ClassName.get(corePackage, "JsonParser");
    }

    ClassName stdSerializer() {
        return ClassName.get(serStdPackage, "StdSerializer");
    }

    ClassName stdDeserializer() {
        return ClassName.get(deserStdPackage, "StdDeserializer");
    }

    ClassName serializerProvider() {
        return ClassName.get(databindPackage, serializerProviderClass);
    }

    ClassName deserializationContext() {
        return ClassName.get(databindPackage, deserializationContextClass);
    }

    ClassName simpleModule() {
        return ClassName.get(modulePackage, "SimpleModule");
    }

    ClassName simpleSerializers() {
        return ClassName.get(modulePackage, "SimpleSerializers");
    }

    ClassName simpleDeserializers() {
        return ClassName.get(modulePackage, "SimpleDeserializers");
    }

    ClassName setupContext() {
        return ClassName.get(databindPackage, setupContextOuterClass, "SetupContext");
    }

    ClassName objectMapper() {
        return ClassName.get(objectMapperPackage, "ObjectMapper");
    }

    ClassName valueSerializer() {
        return ClassName.get(databindPackage, valueSerializerClass);
    }

    ClassName valueDeserializer() {
        return ClassName.get(databindPackage, valueDeserializerClass);
    }
}
