package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * A service provider interface which is called to generate a file per domain primitive.
 * <p>
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 * </p>
 */
@ApiStatus.Experimental
public non-sealed interface MultipleFilesGenerator extends SpiGenerator {
    /**
     * Called for each domain primitive annotated with {@link de.qualityminds.lazyval.LazyValue}.
     * @param element the element annotated with {@link de.qualityminds.lazyval.LazyValue}
     * @param userSettings provided {@link de.qualityminds.lazyval.processor.spi.SpiGenerator.Settings}
     */
    JavaFile generateFilePerType(ValidatedGeneratorElement element, Settings userSettings);
}
