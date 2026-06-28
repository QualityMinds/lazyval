package com.qualityminds.lazyval.testkit.scenarios;

import com.qualityminds.lazyval.testkit.dependencies.Dependency;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.eclipse.collections.impl.collector.Collectors2.toImmutableList;

/**
 * Provides a nicer API to create scenarios by placing optional settings behind a Builder-pattern and
 * @param <T> the type of the scenario to create
 */
public class ScenarioFactory<T extends Scenario> {
    private final String name;
    private final ImmutableCollection<File> sources;
    private final java.util.function.Function<Scenario.Descriptor, T> constructionFunction;
    private ImmutableCollection<Dependency> dependencies = Lists.immutable.empty();
    private ImmutableCollection<String> disabledGenerators = Lists.immutable.empty();
    private boolean basePackageDisabled = false;
    private Map<String, String> options = new HashMap<>();

    /**
     * Derives a display name from a classpath-relative source path by taking the filename segment
     * (everything after the last slash). Used by {@code ofSingle(...)} factories to keep scenario
     * call-sites terse.
     *
     * @param sourcePath classpath-relative path of a source file, e.g. {@code "scenarios/java/Quantity.java"}
     * @return the filename segment (no directory prefix), e.g. {@code "Quantity.java"}
     */
    protected static String deriveName(String sourcePath) {
        int slash = sourcePath.lastIndexOf('/');
        return slash < 0 ? sourcePath : sourcePath.substring(slash + 1);
    }

    /**
     * Returns the name of the scenario this factory should build.
     * <p>
     * Convenience method to access the name from test definitions before actually running the scenario and thus
     * building from the factory.
     * @return the name of the scenario
     */
    public String name(){
        return name;
    }

    @SuppressWarnings("doclint:accessibility,missing")
    protected ScenarioFactory(java.util.function.Function<Scenario.Descriptor, T> constructionFunction, String name, String... sources) {
        this.name = name;
        this.sources = Arrays.stream(sources)
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
     * Convenience method in favor of {@link #withOption(String, String)}.
     * @param disabledGenerators the generators to disable
     * @return this scenario factory
     */
    public ScenarioFactory<T> withDisabledGenerators(String... disabledGenerators) {
        this.disabledGenerators = Lists.immutable.of(disabledGenerators);
        return this;
    }

    /**
     * Disables the default base-package (test) configuration which the factory passes down to the scenario.
     * Convenience method in favor of {@link #withOption(String, String)}.
     * @return this scenario factory
     */
    public ScenarioFactory<T> withDisabledBasePackage() {
        basePackageDisabled = true;
        return this;
    }

    /**
     * Appends an additional processor-option to the scenario.
     * @param key the processor option key
     * @param value the option value
     */
    public void withOption(String key, String value) {
        // null-checks needed for cases where JSpecify is not used.
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        options.put(key, value);
    }

    /**
     * Builds the scenario.
     * @return scenario to be used by testkit
     */
    public T build() {
        if(!disabledGenerators.isEmpty()){
            options.put("lazyval.generators.disable", String.join(",", disabledGenerators));
        }
        if(!basePackageDisabled){
            options.put("lazyval.generators.basePackage", "test");
        }
        var descriptor = new Scenario.Descriptor(name, sources, dependencies, Maps.immutable.ofMap(options));
        return constructionFunction.apply(descriptor);
    }
}
