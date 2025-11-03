package de.qualityminds.lazyval.processor;

import de.qualityminds.lazyval.processor.spi.SpiGenerator;
import de.qualityminds.lazyval.processor.spi.MultipleFilesGenerator;
import de.qualityminds.lazyval.processor.spi.SingleFileGenerator;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

            loadGenerators()
                    .flatMap(generator -> {
                        if(generator instanceof SingleFileGenerator singleFileGenerator){
                            return singleFileGenerator.generateSingleFile(validatedElements, layzvalEnvironment).stream();
                        }else if(generator instanceof MultipleFilesGenerator multipleFilesGenerator){
                            return validatedElements.stream().map(element -> multipleFilesGenerator.generateFilePerType(element, layzvalEnvironment));
                        }else{
                            // move to switch-pattern-match once Java 21 is the minimum required version
                            throw new IllegalStateException("Unknown generator type: " + this.getClass().getName());
                        }
                    })
                    .forEach(fileSpec -> {
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

    private Stream<SpiGenerator> loadGenerators(){

        ServiceLoader<SingleFileGenerator> singleFileGenerators =
                ServiceLoader.load(SingleFileGenerator.class);
        ServiceLoader<MultipleFilesGenerator> multipleFilesGenerators =
                ServiceLoader.load(MultipleFilesGenerator.class);

        boolean hasSingle = singleFileGenerators.iterator().hasNext();
        boolean hasMultiple = multipleFilesGenerators.iterator().hasNext();

        if (!hasSingle && !hasMultiple) {
            layzvalEnvironment.warn("No generators found");
            return Stream.empty();
        }

        return Stream.of(singleFileGenerators, multipleFilesGenerators)
                .flatMap(serviceLoader -> StreamSupport.stream(serviceLoader.spliterator(), false));
    }
}

