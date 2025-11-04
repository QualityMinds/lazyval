package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;


/**
 * A service provider interface which is called to generate a single file for all domain primitives.
 * <p>
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 * </p>
 * <p>
 *     The list of elements is guaranteed to be non-empty.
 * </p>
 */
public non-sealed interface SingleFileGenerator extends SpiGenerator {

    /**
     * Called only once with a list of all domain primitives annotated with {@link de.qualityminds.lazyval.LazyValue}.
     * @param elements all elements annotated with {@link de.qualityminds.lazyval.LazyValue}. Guaranteed to be non-empty.
     * @param userSettings provided {@link de.qualityminds.lazyval.processor.spi.SpiGenerator.Settings}
     */
    JavaFile generateSingleFile(NonEmptySet<ValidatedGeneratorElement> elements, Settings userSettings);
}
