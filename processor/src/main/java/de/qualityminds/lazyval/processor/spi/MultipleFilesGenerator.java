package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;

import java.util.Map;

/**
 * A service provider interface which is called by the {@link de.qualityminds.lazyval.processor.LazyvalProcessor} to
 * generate a file per domain primitive annotated with {@link de.qualityminds.lazyval.LazyValue}.
 * <p>
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 * </p>
 */
public non-sealed interface MultipleFilesGenerator extends SpiGenerator {
    /**
     * Called for each domain primitive annotated with {@link de.qualityminds.lazyval.LazyValue}.
     * @param element the element annotated with {@link de.qualityminds.lazyval.LazyValue}
     * @param userSettings provided {@link de.qualityminds.lazyval.processor.spi.SpiGenerator.Settings}
     */
    JavaFile generateFilePerType(ValidatedGeneratorElement element, Settings userSettings);
}
