package com.qualityminds.lazyval.ksp.internal.codegen.beanvalidation

import com.palantir.javapoet.JavaFile
import com.qualityminds.lazyval.collections.NonEmptySet
import com.qualityminds.lazyval.ksp.internal.codegen.GeneratedStamp.addGeneratedAnnotation
import com.qualityminds.lazyval.ksp.spi.Generator
import com.qualityminds.lazyval.ksp.spi.GeneratorResult
import com.qualityminds.lazyval.ksp.spi.ValidatedKspGeneratorElement
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import java.util.stream.Stream
import javax.lang.model.element.Modifier
import com.palantir.javapoet.AnnotationSpec as JAnnotationSpec
import com.palantir.javapoet.ClassName as JClassName
import com.palantir.javapoet.ParameterizedTypeName as JParameterizedTypeName
import com.palantir.javapoet.TypeName as JTypeName
import com.palantir.javapoet.TypeSpec as JTypeSpec

/**
 * Generates a `ValueExtractor` for each domain-primitive, delegating all constraint validation
 * back to the Bean Validation provider's built-in validators.
 *
 * Two files are produced per domain-primitive:
 * - An **abstract Java base class** (`*ValueExtractorBase.java`) that declares the
 *   `ValueExtractor<@ExtractedValue(...) DomainType>` superinterface.
 * - A **Kotlin implementation class** (`*ValueExtractor.kt`) that extends the base class
 *   and provides the `extractValues` body.
 *
 * The base class must be written in Java. The Kotlin compiler does not emit
 * `RuntimeVisibleTypeAnnotations` for type-use annotations on generic supertype arguments
 * (KT-19289). Jakarta Bean Validation providers such as Hibernate Validator discover
 * `@ExtractedValue` by calling `Class.getAnnotatedInterfaces()` at runtime, which requires
 * that JVM bytecode attribute. Only `javac` emits it correctly for this construct.
 *
 * Extractors are registered via `META-INF/services/jakarta.validation.valueextraction.ValueExtractor`
 * and are therefore discovered automatically by any compliant Bean Validation provider.
 */
class BeanValidationGenerator : Generator {

    companion object {
        private const val GENERATOR_ID = "beanvalidation"
        private const val OPTION_GENERATED_PACKAGE = "lazyval.beanvalidation.package"

        private val J_VALUE_EXTRACTOR = JClassName.get("jakarta.validation.valueextraction", "ValueExtractor")
        private val J_EXTRACTED_VALUE = JClassName.get("jakarta.validation.valueextraction", "ExtractedValue")

        private val K_VALUE_RECEIVER =
            ClassName("jakarta.validation.valueextraction", "ValueExtractor", "ValueReceiver")
        private val UNWRAP_ANNOTATION = AnnotationSpec.builder(
            ClassName("jakarta.validation.valueextraction", "UnwrapByDefault"))
            .build()
    }

    override fun generatorId(): String = GENERATOR_ID

    override fun requiredClasspath(): Collection<String> =
        listOf("jakarta.validation.valueextraction.ValueExtractor")

    override fun supportedOptions(): Set<String> = setOf(OPTION_GENERATED_PACKAGE)

    override fun generate(
        validatedElements: NonEmptySet<ValidatedKspGeneratorElement>,
        context: Generator.Context
    ): Stream<GeneratorResult> {
        val packageName = context.generatorPackage(OPTION_GENERATED_PACKAGE, null)
        return validatedElements.stream().flatMap { buildValueExtractor(context, it, packageName) }
    }

    private fun buildValueExtractor(
        context: Generator.Context,
        element: ValidatedKspGeneratorElement,
        packageName: String
    ): Stream<GeneratorResult> {
        val baseClassName = "${element.typeName.name}ValueExtractorBase"
        val implClassName = "${element.typeName.name}ValueExtractor"

        val javaFile = buildAbstractBase(element, packageName, baseClassName, implClassName, context)
        val fileSpec = buildKotlinImpl(element, packageName, implClassName, baseClassName, context)

        return toResultStream(javaFile, baseClassName, fileSpec, implClassName, packageName)
    }

    /**
     * Builds the abstract Java base class that carries the `@ExtractedValue` type annotation on
     * the `ValueExtractor` superinterface.
     *
     * The class is empty by design: its sole purpose is to let `javac` emit
     * `RuntimeVisibleTypeAnnotations` for the type-use annotation on the generic argument,
     * which the Kotlin compiler cannot do (KT-19289).
     */
    private fun buildAbstractBase(
        element: ValidatedKspGeneratorElement,
        packageName: String,
        baseClassName: String,
        implClassName: String,
        context: Generator.Context
    ): JavaFile {
        val lazyvalJavaClassName = nestedAwareJavaClassName(element)
        val wrappedTypeName = getJavaTypeName(element)
        val wrappedTypeForAnnotation: JTypeName =
            if (element.wrappedProperty.isPrimitive()) wrappedTypeName.box() else wrappedTypeName

        val extractedValueAnnotation = JAnnotationSpec.builder(J_EXTRACTED_VALUE)
            .addMember("type", "\$T.class", wrappedTypeForAnnotation)
            .build()

        val superInterface = JParameterizedTypeName.get(
            J_VALUE_EXTRACTOR,
            lazyvalJavaClassName.annotated(listOf(extractedValueAnnotation))
        )

        val javadoc = "Abstract base class providing the {@code ValueExtractor} superinterface\n" +
            "declaration for {@code ${element.typeName}}.\n\n" +
            "<p>This class must be written in Java. The Kotlin compiler does not emit\n" +
            "{@code RuntimeVisibleTypeAnnotations} for type-use annotations on generic supertype\n" +
            "arguments\n" +
            "(see <a href=\"https://youtrack.jetbrains.com/issue/KT-19289\">KT-19289</a>).\n" +
            "Jakarta Bean Validation providers such as Hibernate Validator discover\n" +
            "{@code @ExtractedValue} by calling {@link Class#getAnnotatedInterfaces()} at runtime,\n" +
            "which relies on that JVM bytecode attribute. Placing the superinterface declaration in\n" +
            "Java source ensures {@code javac} emits the required attribute, making the extractor\n" +
            "discoverable by any compliant Bean Validation provider. The concrete implementation is\n" +
            "provided by {@code $implClassName}.\n"

        val typeSpec = JTypeSpec.classBuilder(baseClassName)
            .addGeneratedAnnotation(BeanValidationGenerator::class, context)
            .addJavadoc(javadoc)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addSuperinterface(superInterface)
            .build()

        return JavaFile.builder(packageName, typeSpec)
            .skipJavaLangImports(true)
            .build()
    }

    private fun buildKotlinImpl(
        element: ValidatedKspGeneratorElement,
        packageName: String,
        implClassName: String,
        baseClassName: String,
        context: Generator.Context
    ): FileSpec {
        val lazyvalTypeName = element.element.toClassName()
        val baseClassTypeName = ClassName(packageName, baseClassName)

        val extractValuesFunction = FunSpec.builder("extractValues")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("originalValue", lazyvalTypeName.copy(nullable = true))
            .addParameter("receiver", K_VALUE_RECEIVER)
            .beginControlFlow("if (originalValue == null)")
            .addStatement("receiver.value(null, null)")
            .addStatement("return")
            .endControlFlow()
            .addStatement("receiver.value(null, originalValue.%L)", element.kotlinAccessor)
            .build()

        val typeSpec = TypeSpec.classBuilder(implClassName)
            .addGeneratedAnnotation(BeanValidationGenerator::class, context)
            .addAnnotation(UNWRAP_ANNOTATION)
            .superclass(baseClassTypeName)
            .addFunction(extractValuesFunction)
            .build()

        return FileSpec.builder(packageName, implClassName).addType(typeSpec).build()
    }

    private fun getJavaTypeName(element: ValidatedKspGeneratorElement): JTypeName {
        val ksType = element.wrappedProperty.type
        return when (ksType.declaration.simpleName.asString()) {
            "Int" -> JTypeName.INT
            "Long" -> JTypeName.LONG
            "Short" -> JTypeName.SHORT
            "Byte" -> JTypeName.BYTE
            "Double" -> JTypeName.DOUBLE
            "Float" -> JTypeName.FLOAT
            "Boolean" -> JTypeName.BOOLEAN
            "Char" -> JTypeName.CHAR
            "String" -> JClassName.get("java.lang", "String")
            else -> {
                val pkg = ksType.declaration.packageName.asString()
                val name = ksType.declaration.simpleName.asString()
                JClassName.get(pkg, name)
            }
        }
    }

    private fun nestedAwareJavaClassName(element: ValidatedKspGeneratorElement): JClassName {
        val elementPackageName = element.element.packageName.asString()
        val simpleNames = element.typeName.value.split(".")
        return JClassName.get(elementPackageName, simpleNames.first(), *simpleNames.drop(1).toTypedArray())
    }

    fun toResultStream(
        javaFile: JavaFile,
        baseClassName: String,
        fileSpec: FileSpec,
        implClassName: String,
        packageName: String
    ): Stream<GeneratorResult> {
        val baseMetadata = GeneratorResult.Metadata(packageName, baseClassName)
        val implMetadata = GeneratorResult.Metadata(packageName, implClassName)
        return Stream.of(
            GeneratorResult.Java(baseMetadata, javaFile.toString()),
            GeneratorResult.Kotlin(implMetadata, fileSpec.toString()),
            GeneratorResult.ServiceLoader(
                GeneratorResult.Metadata("jakarta.validation.valueextraction", "ValueExtractor"),
                implMetadata
            )
        )
    }
}
