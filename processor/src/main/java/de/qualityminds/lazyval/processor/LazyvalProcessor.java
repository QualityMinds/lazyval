package de.qualityminds.lazyval.processor;

import de.qualityminds.lazyval.collections.NonEmptySet;
import de.qualityminds.lazyval.processor.spi.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A JSR 269 annotation Processor which delegates domain-primitives to code generators provided via SPI.
 * <p>
 * A domain-primitive is a class annotated with {@link de.qualityminds.lazyval.LazyValue} or configured
 * via the processor option {@code lazyval.values}
 */
@SupportedAnnotationTypes("de.qualityminds.lazyval.LazyValue")
public class LazyvalProcessor extends AbstractProcessor {

    private static final List<? extends SpiGenerator> allProviderGenerators;

    static {
        // To load the supported-options it is necessary to load all provided generators in this static block
        // because it is not guaranteed that "init" is called before "getSupportedOptions"
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(LazyvalProcessor.class.getClassLoader());

            ServiceLoader<SingleFileGenerator> singleFileGenerators =
                    ServiceLoader.load(SingleFileGenerator.class);
            ServiceLoader<FilePerTypeGenerator> multipleFilesGenerators =
                    ServiceLoader.load(FilePerTypeGenerator.class);

            allProviderGenerators = Stream.of(singleFileGenerators, multipleFilesGenerators)
                    .flatMap(serviceLoader -> StreamSupport.stream(serviceLoader.spliterator(), false))
                    .toList();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @SuppressWarnings("doclint:accessibility,missing")
    public LazyvalProcessor() {
        // must be public for ServiceLoader
    }

    /**
     * Needed to transport originating element(s) through the stream.
     */
    private sealed interface InternalResult {
        record Java(GeneratorResult.Metadata metadata, String contents, List<Element> originatingElements) implements InternalResult {}
        record Nothing() implements InternalResult {}

        InternalResult NOTHING = new Nothing();
    }

    private boolean classpathWarningAlreadyIssued = false;
    private LazyvalEnvironment lazyvalEnvironment;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        lazyvalEnvironment = new LazyvalEnvironment(processingEnv);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        var combinedOptions = allProviderGenerators.stream()
                .flatMap(generator -> generator.supportedOptions().stream())
                .collect(Collectors.toCollection(HashSet::new));
        combinedOptions.addAll(Set.of(
                LazyvalEnvironment.DISABLED_GENERATORS,
                LazyvalEnvironment.CONFIGURED_VALUES
        ));
        return combinedOptions;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for(TypeElement annotation : annotations){
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            var configuredElements = lazyvalEnvironment.getConfiguredValues();

            Set<ValidatedGeneratorElement> validatedElements = Stream.concat(annotatedElements.stream(), configuredElements.stream())
                    .map(element -> lazyvalEnvironment.validateElement((TypeElement) element))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet());
            if(validatedElements.isEmpty()){
                return false;
            }
            getActiveGenerators()
                    .flatMap(generator -> {
                        var settings = processingEnv.getOptions().entrySet().stream().filter(e -> e.getKey().startsWith("lazyval." + generator.generatorId() + "."))
                                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
                        if(generator instanceof SingleFileGenerator singleFileGenerator){
                            var generatorResult = singleFileGenerator.generateSingleFile(NonEmptySet.ofAll(validatedElements), new SpiGenerator.Settings(settings));
                            if(generatorResult instanceof GeneratorResult.Java javaResult) {
                                return Stream.of(new InternalResult.Java(javaResult.metadata(), javaResult.contents(), validatedElements.stream().map(ValidatedGeneratorElement::element).collect(Collectors.toList())));
                            }else if(generatorResult instanceof GeneratorResult.Nothing){
                                return Stream.of(InternalResult.NOTHING);
                            }else {
                                throw new IllegalStateException("Unknown generator result type: " + generatorResult.getClass().getName());
                            }
                        }else if(generator instanceof FilePerTypeGenerator filePerTypeGenerator){
                            return validatedElements.stream().map(validatedElement -> {
                                var generatorResult = filePerTypeGenerator.generateFilePerType(validatedElement, new SpiGenerator.Settings(settings));
                                if(generatorResult instanceof GeneratorResult.Java javaResult) {
                                    return new InternalResult.Java(javaResult.metadata(), javaResult.contents(),List.of(validatedElement.element()));
                                }else if(generatorResult instanceof GeneratorResult.Nothing){
                                    return InternalResult.NOTHING;
                                }else {
                                    throw new IllegalStateException("Unknown generator result type: " + generatorResult.getClass().getName());
                                }
                            });
                        }else{
                            // move to switch-pattern-match once Java 21 is the minimum required version
                            throw new IllegalStateException("Unknown generator type: " + this.getClass().getName());
                        }
                    })
                    // remove potential null, since external generators might not use JSpecify annotations/tooling
                    .filter(Objects::nonNull)
                    // TODO check for duplicates
                    .forEach(result -> {
                        if(result instanceof InternalResult.Java javaResult) {
                            writeJavaFile(javaResult);
                        }
                    });
        }

        return false;
    }

    private void writeJavaFile(InternalResult.Java javaResult){
        JavaFileObject filerSourceFile = null;
        try {
            filerSourceFile = processingEnv.getFiler().createSourceFile(
                    javaResult.metadata().className(),
                    javaResult.originatingElements().toArray(new Element[0]));

            try (Writer writer = filerSourceFile.openWriter()) {
                writer.write(javaResult.contents());
            }

        } catch (Exception e) {
            try {
                if (filerSourceFile != null) {
                    filerSourceFile.delete();
                }
            } catch (Exception var8) {
                lazyvalEnvironment.error("Could not delete generated source file. Cause: %s".formatted(e.getMessage()));
            }

            throw new RuntimeException(e);
        }
        lazyvalEnvironment.info("Written '%s.%s".formatted(javaResult.metadata().packageName(), javaResult.metadata().className()));
    }

    /**
     * Loads all generators from the classpath and returns the ones that have their classpath dependencies satisfied
     * and have not been disabled by a configuration option.
     *
     * Has to make use of TCCL to work for complex classloader setup (Spring, Quarkus) as well as simple ones.
     */
    private Stream<? extends SpiGenerator> getActiveGenerators(){
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(LazyvalProcessor.class.getClassLoader());

            boolean hasSingle = allProviderGenerators.stream().anyMatch(g -> g instanceof SingleFileGenerator);
            boolean hasMultiple = allProviderGenerators.stream().anyMatch(g -> g instanceof FilePerTypeGenerator);

            if (!hasSingle && !hasMultiple) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.");
                return Stream.empty();
            }

            var disabledByConfig = lazyvalEnvironment.getDisabledGenerators();

            var generators = allProviderGenerators.stream()
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

