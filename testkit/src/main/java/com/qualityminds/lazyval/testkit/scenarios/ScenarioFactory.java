package com.qualityminds.lazyval.testkit.scenarios;

import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.Lists;

import java.io.File;
import java.util.Arrays;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;

/**
 * Provides a nicer API to create scenarios by placing optional settings behind a Builder-pattern and
 * @param <T> the type of the scenario to create
 */
public class ScenarioFactory<T extends Scenario> {
    private final File sourceFile;
    private final ImmutableCollection<File> additionalSourceFiles;
    private final java.util.function.Function<Scenario.Descriptor, T> constructionFunction;
    private ImmutableCollection<Dependency> dependencies = Lists.immutable.empty();
    private ImmutableCollection<String> disabledGenerators = Lists.immutable.empty();

    /**
     * Returns the name of the source file this scenario is based on.
     * <p>
     * Convenience method to access the name from test definitions before actually running the scenario and thus
     * building from the factory.
     * @return the name of the source file
     */
    public String name(){
        return sourceFile.getName();
    }

    @SuppressWarnings("doclint:accessibility,missing")
    protected ScenarioFactory(java.util.function.Function<Scenario.Descriptor, T> constructionFunction, String source, String... additionalSources) {
        this.sourceFile = Scenario.loadSource(source);
        this.additionalSourceFiles = Arrays.stream(additionalSources)
                .map(Scenario::loadSource)
                .collect(toImmutableList());
        // this function is needed to call either the Java- or the Kotlin-typed constructor from the respective
        // factory-methods
        this.constructionFunction = constructionFunction;
    }

    /**
     * Configures additional classpath-dependencies for the scenario.
     * @param dependencies dependencies to use
     * @return this scenario factory
     */
    public ScenarioFactory<T> withDependencies(Dependency... dependencies) {
        this.dependencies = Lists.immutable.of(dependencies);
        return this;
    }

    /**
     * Configures additional classpath-dependencies for the scenario.
     * @param dependencies dependencies to use
     * @return this scenario factory
     */
    public ScenarioFactory<T> withDependencies(Iterable<Dependency> dependencies) {
        this.dependencies = Lists.immutable.ofAll(dependencies);
        return this;
    }

    /**
     * Disables the given generators for this scenario.
     * @param disabledGenerators the generators to disable
     * @return this scenario factory
     */
    public ScenarioFactory<T> withDisabledGenerators(String... disabledGenerators) {
        this.disabledGenerators = Lists.immutable.of(disabledGenerators);
        return this;
    }

    /**
     * Builds the scenario.
     * @return scenario to be used by testkit
     */
    public T build() {
        var descriptor = new Scenario.Descriptor(sourceFile, additionalSourceFiles, dependencies, disabledGenerators);
        return constructionFunction.apply(descriptor);
    }
}
