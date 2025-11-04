package de.qualityminds.lazyval.testkit.scenarios;

import de.qualityminds.lazyval.testkit.dependencies.Dependency;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.Lists;

import java.io.File;
import java.util.Arrays;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;


public class ScenarioFactory<T extends Scenario> {
    private final File sourceFile;
    private final ImmutableCollection<File> additionalSourceFiles;
    private final java.util.function.Function<Scenario.Descriptor, T> constructor;
    private ImmutableCollection<Dependency> dependencies = Lists.immutable.empty();
    private ImmutableCollection<String> disabledGenerators = Lists.immutable.empty();

    public String name() {
        return sourceFile.getName();
    }

    ScenarioFactory(java.util.function.Function<Scenario.Descriptor, T> constructor, String source, String... additionalSources) {
        this.sourceFile = Scenario.loadSource(source);
        this.additionalSourceFiles = Arrays.stream(additionalSources)
                .map(Scenario::loadSource)
                .collect(toImmutableList());
        this.constructor = constructor;
    }

    public ScenarioFactory<T> withDependencies(Dependency... dependencies) {
        this.dependencies = Lists.immutable.of(dependencies);
        return this;
    }

    public ScenarioFactory<T> withDependencies(Iterable<Dependency> dependencies) {
        this.dependencies = Lists.immutable.ofAll(dependencies);
        return this;
    }

    public ScenarioFactory<T> withDisabledGenerators(String... disabledGenerators) {
        this.disabledGenerators = Lists.immutable.of(disabledGenerators);
        return this;
    }

    public T build() {
        var descriptor = new Scenario.Descriptor(sourceFile, additionalSourceFiles, dependencies, disabledGenerators);
        return constructor.apply(descriptor);
    }
}
