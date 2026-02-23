package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.LazyValue;
import com.qualityminds.lazyval.collections.NonEmptySet;
import org.jetbrains.annotations.ApiStatus;


/**
 * A service provider interface which is called to generate a single file for all domain primitives.
 * <p>
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 * </p>
 * <p>
 *     The list of elements is guaranteed to be non-empty.
 * </p>
 */
@ApiStatus.Experimental
public non-sealed interface SingleFileGenerator extends SpiGenerator {

    /**
     * Called only once with a list of all domain primitives annotated with {@link LazyValue}.
     * @param elements all elements annotated with {@link LazyValue}. Guaranteed to be non-empty.
     * @param userSettings provided {@link SpiGenerator.Settings}
     * @return {@link GeneratorResult.Java} for the generated file or {@link GeneratorResult.Nothing}
     */
    GeneratorResult generateSingleFile(NonEmptySet<ValidatedGeneratorElement> elements, Settings userSettings);
}
