package com.qualityminds.lazyval.ksp.internal.codegen.jackson

import com.qualityminds.lazyval.ksp.spi.Generator
import com.squareup.kotlinpoet.ClassName
import kotlin.reflect.KClass

/**
 * Captures the package/class differences between Jackson 2 and Jackson 3.
 */
@Suppress("TooManyFunctions") // Centralizes Jackson 2/3 type projections; splitting would scatter version-difference data.
internal enum class JacksonVersion(
    val spiPackage: String,
    val spiClass: String,
    val corePackage: String,
    val databindPackage: String,
    val serStdPackage: String,
    val deserStdPackage: String,
    val modulePackage: String,
    val serializerProviderClass: String,
    val deserializationContextClass: String,
    val setupContextOuterClass: String,
    val objectMapperPackage: String,
    val lazyvalJacksonModuleName: String,
    val valueSerializerClass: String,
    val valueDeserializerClass: String,
    val executingGenerator: KClass<out Generator>
) {
    JACKSON_2(
        spiPackage = "com.fasterxml.jackson.databind",
        spiClass = "Module",
        corePackage = "com.fasterxml.jackson.core",
        databindPackage = "com.fasterxml.jackson.databind",
        serStdPackage = "com.fasterxml.jackson.databind.ser.std",
        deserStdPackage = "com.fasterxml.jackson.databind.deser.std",
        modulePackage = "com.fasterxml.jackson.databind.module",
        serializerProviderClass = "SerializerProvider",
        deserializationContextClass = "DeserializationContext",
        setupContextOuterClass = "Module",
        objectMapperPackage = "com.fasterxml.jackson.databind",
        lazyvalJacksonModuleName = "LazyvalJackson2Module",
        valueSerializerClass = "JsonSerializer",
        valueDeserializerClass = "JsonDeserializer",
        executingGenerator = Jackson2Generator::class
    ),
    JACKSON_3(
        spiPackage = "tools.jackson.databind",
        spiClass = "JacksonModule",
        corePackage = "tools.jackson.core",
        databindPackage = "tools.jackson.databind",
        serStdPackage = "tools.jackson.databind.ser.std",
        deserStdPackage = "tools.jackson.databind.deser.std",
        modulePackage = "tools.jackson.databind.module",
        serializerProviderClass = "SerializationContext",
        deserializationContextClass = "DeserializationContext",
        setupContextOuterClass = "JacksonModule",
        objectMapperPackage = "tools.jackson.databind",
        lazyvalJacksonModuleName = "LazyvalJacksonModule",
        valueSerializerClass = "ValueSerializer",
        valueDeserializerClass = "ValueDeserializer",
        executingGenerator = Jackson3Generator::class
    );

    fun jsonGenerator(): ClassName = ClassName(corePackage, "JsonGenerator")
    fun jsonParser(): ClassName = ClassName(corePackage, "JsonParser")
    fun stdSerializer(): ClassName = ClassName(serStdPackage, "StdSerializer")
    fun stdDeserializer(): ClassName = ClassName(deserStdPackage, "StdDeserializer")
    fun serializerProvider(): ClassName = ClassName(databindPackage, serializerProviderClass)
    fun deserializationContext(): ClassName = ClassName(databindPackage, deserializationContextClass)
    fun simpleModule(): ClassName = ClassName(modulePackage, "SimpleModule")
    fun simpleSerializers(): ClassName = ClassName(modulePackage, "SimpleSerializers")
    fun simpleDeserializers(): ClassName = ClassName(modulePackage, "SimpleDeserializers")
    fun setupContext(): ClassName = ClassName(databindPackage, setupContextOuterClass, "SetupContext")
    fun objectMapper(): ClassName = ClassName(objectMapperPackage, "ObjectMapper")
    fun valueSerializer(): ClassName = ClassName(databindPackage, valueSerializerClass)
    fun valueDeserializer(): ClassName = ClassName(databindPackage, valueDeserializerClass)
}
