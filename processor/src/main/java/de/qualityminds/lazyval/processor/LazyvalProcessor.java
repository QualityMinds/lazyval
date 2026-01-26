package de.qualityminds.lazyval.processor;

import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.processor.spi.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SupportedAnnotationTypes("de.qualityminds.lazyval.LazyValue")
@SupportedOptions({
        // options coming from external generators cannot be documented
        LazyvalEnvironment.DISABLED_GENERATORS,
        LazyvalEnvironment.CONFIGURED_VALUES,
})
public class LazyvalProcessor extends AbstractProcessor {

    private boolean classpathWarningAlreadyIssued = false;
    private LazyvalEnvironment lazyvalEnvironment;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // initialization can only be done here, since the processingEnv is set via init.
        try {
            lazyvalEnvironment = new LazyvalEnvironment(processingEnv);
        }catch (IllegalArgumentException e){
            return false;
        }

        for(TypeElement annotation : annotations){
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            var configuredElements = lazyvalEnvironment.getConfiguredValues();

            Set<ValidatedGeneratorElement> validatedElements = Stream.concat(annotatedElements.stream(), configuredElements.stream())
                    .map(element -> lazyvalEnvironment.validateElement((TypeElement) element))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
            if(validatedElements.isEmpty()){
                return true;
            }
            loadGenerators()
                    .flatMap(generator -> {
                        var settings = processingEnv.getOptions().entrySet().stream().filter(e -> e.getKey().startsWith("lazyval." + generator.generatorId() + "."))
                                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                        if(generator instanceof SingleFileGenerator singleFileGenerator){
                            return Stream.of(singleFileGenerator.generateSingleFile(NonEmptySet.ofAll(validatedElements), new SpiGenerator.Settings(settings)));
                        }else if(generator instanceof FilePerTypeGenerator filePerTypeGenerator){
                            return validatedElements.stream().map(element -> filePerTypeGenerator.generateFilePerType(element, new SpiGenerator.Settings(settings)));
                        }else{
                            // move to switch-pattern-match once Java 21 is the minimum required version
                            throw new IllegalStateException("Unknown generator type: " + this.getClass().getName());
                        }
                    })
                    // remove potential null, since external generators might not use JSpecify annotations/tooling
                    .filter(Objects::nonNull)
                    // TODO check for duplicates
                    .forEach(result -> {
                        try{
                            if(result instanceof GeneratorResult.Java javaResult) {
                                var fileSpec = javaResult.fileSpec();
                                fileSpec.writeTo(processingEnv.getFiler());
                                lazyvalEnvironment.info("Written '%s.%s".formatted(fileSpec.packageName(), fileSpec.typeSpec().name()));
                            }
                        }catch (IOException e){
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        return true;
    }

    /**
     * Loads all generators from the classpath and returns the ones that have their classpath dependencies satisfied
     * and have not been disabled by a configuration option.
     *
     * Has to make use of TCCL to work for complex classloader setup (Spring, Quarkus) as well as simple ones.
     */
    private Stream<? extends SpiGenerator> loadGenerators(){
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

            ServiceLoader<SingleFileGenerator> singleFileGenerators =
                    ServiceLoader.load(SingleFileGenerator.class);
            ServiceLoader<FilePerTypeGenerator> multipleFilesGenerators =
                    ServiceLoader.load(FilePerTypeGenerator.class);

            boolean hasSingle = singleFileGenerators.iterator().hasNext();
            boolean hasMultiple = multipleFilesGenerators.iterator().hasNext();

            if (!hasSingle && !hasMultiple) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.");
                return Stream.empty();
            }

            var disabledByConfig = lazyvalEnvironment.getDisabledGenerators();

            var generators = Stream.of(singleFileGenerators, multipleFilesGenerators)
                    .flatMap(serviceLoader -> StreamSupport.stream(serviceLoader.spliterator(), false))
                    // TODO check for ID
                    .filter(generator -> generator.requiredClasspath().stream().allMatch(fqn -> lazyvalEnvironment.isClassAvailable(fqn)))
                    .filter(generator -> !disabledByConfig.contains(generator.generatorId()))
                    .toList();

            lazyvalEnvironment.info("Lazyval Active Providers: " + generators.stream().map(SpiGenerator::generatorId).collect(Collectors.joining(", ")));

            if(generators.isEmpty()){
                if(!classpathWarningAlreadyIssued){
                    lazyvalEnvironment.warnMissingClasspath();
                    classpathWarningAlreadyIssued = true;
                }
            }

            return generators.stream();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}

