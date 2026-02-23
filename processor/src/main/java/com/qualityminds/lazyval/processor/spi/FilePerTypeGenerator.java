package com.qualityminds.lazyval.processor.spi;

import com.qualityminds.lazyval.LazyValue;
import org.jetbrains.annotations.ApiStatus;

/**
 * A service provider interface which is called to generate a file per domain primitive.
 * <p>
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 * </p>
 */
@ApiStatus.Experimental
public non-sealed interface FilePerTypeGenerator extends SpiGenerator {
    /**
     * Called for each domain primitive annotated with {@link LazyValue}.
     * @param validatedElement the element annotated with {@link LazyValue}, after validation
     * @param userSettings provided {@link SpiGenerator.Settings}
     * @return {@link GeneratorResult.Java} for the generated file or {@link GeneratorResult.Nothing}
     */
    GeneratorResult generateFilePerType(ValidatedGeneratorElement validatedElement, Settings userSettings);
}
