package de.qualityminds.lazyval.processor

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class AnnotationProcessorIT extends Specification {

    @TempDir()
    Path tempDir

    ClassLoader classloader;

    void setup() {
        // make sure test-output is english (because the tests check for compiler error messages)
        Locale.setDefault(Locale.ENGLISH)
        this.classloader = this.getClass().getClassLoader()
    }

    void "Running '#fileToCompile' #statusMessage"(){
        given:
        var compilerSetup = CompilerSetup.setupTask(classloader, fileToCompile, tempDir, CompilerSetup.Libraries.ALL, List.of())

        when:
        def result = compilerSetup.run()

        then: 'compilation fails due to missing dependencies'
        result.getTaskResult() == compiles

        and: 'compiler warning when object class is not final'
        result.wasObjectNotFinalWarning() == warnNotFinalIssued

        and: 'compiler warning when object wrapped value not final'
        result.wasValueNotFinalWarning() == warnValueNotFinalIssued

        and: 'Generated Mapstruct Mapper'
        result.generatedFile("LazyvalMapper.java") == compiles

        and: 'Generated AttributeConverter'
        result.generatedFile(fileToCompile.replace(".java", "AttributeConverter.java")) == compiles

        where:
        fileToCompile                       | compiles
        'RecordValid.java'                  | true
        'RecordValidInt.java'               | true
        'RecordValidInteger.java'           | true
        'RecordMoreThanOneProperty.java'    | false
        'RecordMultipleFactories.java'      | false
        'ObjectValid.java'                  | true
        'ObjectValidInt.java'               | true
        'ObjectValidInteger.java'           | true
        'ObjectValidWithoutFactory.java'    | true
        'ObjectNotFinal.java'               | true
        'ObjectValueNotFinal.java'          | true
        'ObjectMissingValueAccessor.java'   | false
        'ObjectMoreThanOneProperty.java'    | false
        'ObjectMultipleFactories.java'      | false
        'AbstractClass.java'                | false
        'ProductId.java'                    | true
        statusMessage = compiles ? 'completes successfully' : 'fails the processing due to missing requirements'
        warnNotFinalIssued = fileToCompile == 'ObjectNotFinal.java'
        warnValueNotFinalIssued = fileToCompile == 'ObjectValueNotFinal.java'
    }

    void "Generates no Code but instead issues a warning when neither Mapstruct, nor JPA is available"(){
        given: 'using a valid and annotated type'
        var compilerSetup = CompilerSetup.setupTask(classloader, "RecordValid.java", tempDir, CompilerSetup.Libraries.NONE, List.of())

        when:
        def result = compilerSetup.run()

        then: 'compilation succeeds'
        result.getTaskResult()

        and: 'but a warning was emitted'
        result.wasNoGenerationWarning()

        and: 'only once'
        result.getWarnings().size() == 1
    }

    void "Disabling by id '#generatorId' will not generate '#skippedSource'"(){
        given:
        var compilerSetup = CompilerSetup.setupTask(classloader, 'RecordValid.java', tempDir, CompilerSetup.Libraries.ALL, List.of(generatorId))

        when:
        def result = compilerSetup.run()

        then: 'compilation succeeds'
        result.getTaskResult()

        and: 'skipped source generation'
        !result.generatedFile(skippedSource)

        where:
        generatorId  | skippedSource
        'mapstruct'  | 'LazyvalMapper.java'
        'jpa'        | 'RecordValidAttributeConverter.java'
    }
}