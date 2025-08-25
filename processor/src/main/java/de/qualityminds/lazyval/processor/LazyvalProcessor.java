package de.qualityminds.lazyval.processor;

import com.palantir.javapoet.JavaFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedAnnotationTypes("de.qualityminds.lazyval.LazyValue")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions({
        LazyvalEnvironment.JPA_GENERATED_PACKAGE,
        LazyvalEnvironment.MAPSTRUCT_GENERATED_PACKAGE,
})
public class LazyvalProcessor extends AbstractProcessor {

    private boolean classpathWarningAlreadyIssued = false;
    private LazyvalEnvironment layzvalEnvironment;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // initialization can only be done here, since the processingEnv is set via init.
        try {
            layzvalEnvironment = new LazyvalEnvironment(processingEnv);
        }catch (IllegalArgumentException e){
            // logs already printed
            return false;
        }

        if(layzvalEnvironment.isJpaMissingClasspath() && layzvalEnvironment.isMapstructMissingOnClasspath()){
            if(!classpathWarningAlreadyIssued){
                layzvalEnvironment.warnMissingClasspath();
                classpathWarningAlreadyIssued = true;
            }
            return true;
        }

        for(TypeElement annotation : annotations){
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            Set<ValidatedGeneratorElement> validatedElements = annotatedElements.stream()
                    .map(element -> layzvalEnvironment.validateElement((TypeElement) element))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
            Stream.concat(
                    MapstructGenerator.createMapstructMapper(validatedElements, layzvalEnvironment),
                    createJpaAttributeConverter(validatedElements)
            ).forEach(fileSpec -> {
                try{
                    fileSpec.writeTo(processingEnv.getFiler());
                    layzvalEnvironment.info("Written '%s.%s".formatted(fileSpec.packageName(), fileSpec.typeSpec().name()));
                }catch (IOException e){
                    throw new UncheckedIOException(e);
                }
            });
        }

        return true;
    }


    private Stream<JavaFile> createJpaAttributeConverter(Set<ValidatedGeneratorElement> elements){
        if(layzvalEnvironment.isJpaMissingClasspath()){
            layzvalEnvironment.info("JPA is not on classpath. Lazyval will not generate AttributeConverters.");
            return Stream.empty();
        }
        return elements.stream().map(element -> JpaGenerator.createJpaAttributeConverter(element, layzvalEnvironment));

    }
}

