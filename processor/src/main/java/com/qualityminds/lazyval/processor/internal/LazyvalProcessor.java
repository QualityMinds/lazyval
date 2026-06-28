package com.qualityminds.lazyval.processor.internal;

import com.qualityminds.lazyval.LazyValue;
import com.qualityminds.lazyval.LazyvalConfiguration;
import com.qualityminds.lazyval.collections.NonEmptySet;
import com.qualityminds.lazyval.processor.internal.codegen.jackson.Jackson2Generator;
import com.qualityminds.lazyval.processor.internal.codegen.jackson.Jackson3Generator;
import com.qualityminds.lazyval.processor.spi.Generator;
import com.qualityminds.lazyval.processor.spi.GeneratorResult;
import com.qualityminds.lazyval.processor.spi.ValidatedGeneratorElement;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A JSR 269 annotation Processor which delegates domain-primitives to code generators provided via SPI.
 * <p>
 * A domain-primitive is a class annotated with {@link LazyValue}, or an external type listed in
 * {@link LazyvalConfiguration#externalTypes()} on the module's {@code package-info.java}.
 */
@SupportedAnnotationTypes({
        "com.qualityminds.lazyval.LazyValue",
        "com.qualityminds.lazyval.LazyvalConfiguration"
})
public class LazyvalProcessor extends AbstractProcessor {

    private static final List<? extends Generator> allProviderGenerators;


    static {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(LazyvalProcessor.class.getClassLoader());

            allProviderGenerators = ServiceLoader.load(Generator.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
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
        record ServiceLoader(GeneratorResult.Metadata spiType, GeneratorResult.Metadata providerType, List<Element> originatingElements) implements InternalResult {}
    }

    private boolean classpathWarningAlreadyIssued = false;
    private LazyvalEnvironment lazyvalEnvironment;
    private LazyvalElementValidator elementValidator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        lazyvalEnvironment = new LazyvalEnvironment(processingEnv);
        elementValidator = new LazyvalElementValidator(lazyvalEnvironment.typeUtils(), lazyvalEnvironment);
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
                LazyvalEnvironment.BASE_PACKAGE
        ));
        return combinedOptions;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var lazyValueType = processingEnv.getElementUtils()
                .getTypeElement(LazyValue.class.getCanonicalName());
        Set<? extends Element> annotatedElements = lazyValueType != null
                ? roundEnv.getElementsAnnotatedWith(lazyValueType)
                : Set.of();
        var configuredElements = lazyvalEnvironment.getConfiguredValues(roundEnv);

        // Sort by qualified type name so the iteration order is deterministic across runs.
        // Generators that emit a single file containing entries for every element (Jackson modules, etc.)
        // rely on stable input order to produce byte-identical output; approval tests fail otherwise.
        // Multi-file generators are unaffected.
        List<ValidatedGeneratorElement> validatedElements = Stream.concat(annotatedElements.stream(), configuredElements.stream())
                .map(element -> elementValidator.validate((TypeElement) element))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(e -> e.element().getQualifiedName().toString()))
                .toList();
        if (validatedElements.isEmpty()) {
            return false;
        }

        List<Element> orignatingElements = validatedElements.stream().map(ValidatedGeneratorElement::element).collect(Collectors.toList());

        List<InternalResult> results = getActiveGenerators()
                .flatMap(generator -> callGenerator(generator, validatedElements, orignatingElements))
                .toList();

        // Write Java files immediately
        results.stream()
                .filter(r -> r instanceof InternalResult.Java)
                .map(r -> (InternalResult.Java) r)
                .forEach(this::writeJavaFile);

        writeServiceLoaderFiles(results.stream()
                .filter(r -> r instanceof InternalResult.ServiceLoader)
                .map(r -> (InternalResult.ServiceLoader) r)
                .toList());
        return false;
    }

    /**
     * Loads all generators from the classpath and returns the ones that have their classpath dependencies satisfied
     * and have not been disabled by a configuration option.
     * <p>
     * Has to make use of TCCL to work for complex classloader setup (Spring, Quarkus) as well as simple ones.
     */
    private Stream<? extends Generator> getActiveGenerators(){
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(LazyvalProcessor.class.getClassLoader());

            if (allProviderGenerators.isEmpty()) {
                lazyvalEnvironment.warn("No Lazyval SPI providers found on classpath.");
                return Stream.empty();
            }

            var disabledByConfig = lazyvalEnvironment.getDisabledGenerators();

            var generators = allProviderGenerators.stream()
                    .filter(generator -> generator.requiredClasspath().stream().allMatch(fqn -> lazyvalEnvironment.isClassAvailable(fqn)))
                    .filter(generator -> !disabledByConfig.contains(generator.generatorId()))
                    .toList();

            lazyvalEnvironment.info("Active Providers: " + generators.stream().map(Generator::generatorId).collect(Collectors.joining(", ")));

            var activeIds = generators.stream().map(Generator::generatorId).collect(Collectors.toSet());
            if (activeIds.contains("cassandra") && activeIds.contains("cassandra-spring-data")) {
                lazyvalEnvironment.info("Both 'cassandra' and 'cassandra-spring-data' generators are active. " +
                        "You can disable one via 'lazyval.generators.disable' if only one integration is needed.");
            }
            if (activeIds.contains("mongodb") && activeIds.contains("spring-data")
                    && lazyvalEnvironment.isClassAvailable("org.springframework.data.mongodb.core.convert.MongoCustomConversions")) {
                lazyvalEnvironment.info("Both 'mongodb' and 'spring-data' generators are active with spring-data-mongodb on the classpath. " +
                        "You can disable one via 'lazyval.generators.disable' if only one integration is needed.");
            }
            if (activeIds.contains(Jackson3Generator.GENERATOR_ID) && activeIds.contains(Jackson2Generator.GENERATOR_ID)) {
                lazyvalEnvironment.warn("""
                        Both 'jackson-2' and 'jackson-3' generators are active (probably due to transitive dependencies). \
                        This might be intentional, then ignore this warning. \
                        Otherwise, disable via one 'lazyval.generators.disable'""");
            }

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

    private Stream<InternalResult> callGenerator(Generator generator, List<ValidatedGeneratorElement> validatedElements, List<Element> orignatingElements) {
        //noinspection ConstantValue
        return generator.generate(
                // NonEmptySet uses a LinkedHashSet internally, so the sorted order established in
                // process() is preserved when generators iterate the set.
                NonEmptySet.ofAll(validatedElements),
                        lazyvalEnvironment.createContext(validatedElements.iterator().next()))
                // remove potential null, since external generators might not use JSpecify annotations/tooling
                .filter(Objects::nonNull)
                .map(output -> mapToInternalOutput(output, orignatingElements));
    }

    private static InternalResult mapToInternalOutput(GeneratorResult output, List<Element> orignatingElements) {
        if(output instanceof GeneratorResult.Java javaResult) {
            return new InternalResult.Java(javaResult.metadata(), javaResult.contents(), orignatingElements);
        }else if(output instanceof GeneratorResult.ServiceLoader serviceLoaderResult) {
            return new InternalResult.ServiceLoader(serviceLoaderResult.spiType(), serviceLoaderResult.providerType(), orignatingElements);
        } else {
            throw new IllegalStateException("Unknown generator result typeMirror: " + output.getClass().getName());
        }
    }

    private void writeJavaFile(InternalResult.Java javaResult){
        JavaFileObject filerSourceFile = null;
        try {
            filerSourceFile = processingEnv.getFiler().createSourceFile(
                    javaResult.metadata().qualifiedName(),
                    javaResult.originatingElements().toArray(new Element[1]));

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
        lazyvalEnvironment.info("Written '%s'".formatted(javaResult.metadata().qualifiedName()));
    }

    private void writeServiceLoaderFiles(List<InternalResult.ServiceLoader> serviceLoaderResults) {
        // group all providers by spi-type in order to place them in a single file
        var groupedBySpiType = serviceLoaderResults.stream()
                .collect(Collectors.groupingBy(sl -> sl.spiType().qualifiedName()));

        for (var entry : groupedBySpiType.entrySet()) {
            String spiTypeName = entry.getKey();
            List<InternalResult.ServiceLoader> group = entry.getValue();

            Element[] allOriginatingElements = group.stream()
                    .flatMap(sl -> sl.originatingElements().stream())
                    .distinct()
                    .toArray(Element[]::new);

            String fileContent = group.stream()
                    .map(sl -> sl.providerType().qualifiedName())
                    .distinct()
                    .collect(Collectors.joining("\n"));

            FileObject filerResource = null;
            try {
                filerResource = processingEnv.getFiler().createResource(
                        StandardLocation.CLASS_OUTPUT,
                        "",
                        "META-INF/services/" + spiTypeName,
                        allOriginatingElements);

                try (Writer writer = filerResource.openWriter()) {
                    writer.write(fileContent);
                }

            } catch (Exception e) {
                try {
                    if (filerResource != null) {
                        filerResource.delete();
                    }
                } catch (Exception var8) {
                    lazyvalEnvironment.error("Could not delete generated source file. Cause: %s".formatted(e.getMessage()));
                }

                throw new RuntimeException(e);
            }
            lazyvalEnvironment.info("Written 'META-INF/services/%s' with %d provider(s)".formatted(
                    spiTypeName, group.size()));
        }
    }
}

