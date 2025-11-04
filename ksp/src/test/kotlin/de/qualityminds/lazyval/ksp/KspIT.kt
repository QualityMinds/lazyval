package de.qualityminds.lazyval.ksp

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

class KspIT : DescribeSpec({

    data class TestCase(val fileToCompile: String, val compiles: Boolean, val generatedSources: Boolean = true){
        fun message() = "Compiling '${fileToCompile}' should ${if(compiles) "succeed" else "fail"}"
    }

    context("Run KSP2 and Compiler"){
        withData<TestCase>(
            nameFn = { it.message() },
            TestCase("Isbn.kt", true),
            TestCase("IsbnWithAccessor.kt", true),
            TestCase("IsbnMissingFactory.kt", false),
            TestCase("Quantity.kt", true),
            TestCase("QuantityWontCompile.kt", false, true), // since the compiler runs after ksp, files will be generated
            TestCase("MultiplePropertyClass.kt", false, false),
            TestCase("MultiplePropertyDataClass.kt", false, false),
            TestCase("AbstractClass.kt", false, false),
            TestCase("ValueClass.kt", false, false),
            TestCase("NullableQuantity.kt", true, true),
            TestCase("ProductId.kt", true, true),
            TestCase("MultipleFactoriesClass.kt", false, false),
        ){ (fileToCompile, compiles, generatedSources) ->
            val projectFolder = tempdir(prefix = "ksp", keepOnFailure = true).toPath()
            val setup = ToolchainSetup.setupTask(
                this::class.java.classLoader,
                fileToCompile,
                projectFolder,
                ToolchainSetup.Libraries.ALL,
                emptyList()
            )
            val result = setup.run()

            runCatching {
                result.isSuccessful() shouldBe compiles
                result.generatedJavaFile("LazyvalMapper.java") shouldBe generatedSources
                result.generatedKotlinFile(fileToCompile.replace(".kt", "AttributeConverter.kt")) shouldBe generatedSources
            }.onFailure {
                result.printDebugMessages()
            }.onSuccess {
                // this makes sure only the failing iterations are kept
                projectFolder.toFile().deleteRecursively()
            }.getOrThrow() // Re-throw to fail the test
        }
    }

    it("Does not create any files when dependencies are missing"){
        val projectFolder = tempdir(prefix = "ksp", keepOnFailure = true).toPath()
        val fileToCompile = "Quantity.kt"
        val setup = ToolchainSetup.setupTask(
            this::class.java.classLoader,
            fileToCompile,
            projectFolder,
            ToolchainSetup.Libraries.NONE,
            emptyList()
        )
        val result = setup.run()

        result.isSuccessful() shouldBe true
        result.generatedJavaFiles shouldBe emptyList()
        result.generatedKotlinFiles shouldBe emptyList()
    }

    data class DisabledGeneratorTestCase(val generatorId: String, val skippedSource: String)

    context("Disabled generators") {
        withData<DisabledGeneratorTestCase>(
            nameFn = { "Disabling by id '${it.generatorId}' will not generate '${it.skippedSource}'" },
            DisabledGeneratorTestCase("mapstruct", "LazyvalMapper.java"),
            DisabledGeneratorTestCase("jpa", "RecordValidAttributeConverter.kt"),
        ) { (generatorId, skippedSource) ->
            val projectFolder = tempdir(prefix = "ksp", keepOnFailure = true).toPath()
            val setup = ToolchainSetup.setupTask(
                this::class.java.classLoader,
                "Quantity.kt",
                projectFolder,
                ToolchainSetup.Libraries.ALL,
                listOf(generatorId)
            )
            val result = setup.run()

            runCatching {
                result.isSuccessful() shouldBe true
                result.generatedJavaFile(skippedSource) shouldBe false
                result.generatedKotlinFile(skippedSource) shouldBe false
            }.onFailure {
                result.printDebugMessages()
            }.onSuccess {
                // this makes sure only the failing iterations are kept
                projectFolder.toFile().deleteRecursively()
            }.getOrThrow() // Re-throw to fail the test
        }
    }

})