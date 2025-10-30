package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;
import de.qualityminds.lazyval.processor.LazyvalEnvironment;
import de.qualityminds.lazyval.processor.ValidatedGeneratorElement;

/**
 * A service provider interface which is called by the {@link de.qualityminds.lazyval.processor.LazyvalProcessor} to
 * generate a file per domain primitive annotated with {@link de.qualityminds.lazyval.LazyValue}.
 * <p>
 * As an example, for each domain primitive a dedicated JPA AttributeConverter is needed.
 * </p>
 */
public non-sealed interface MultipleFilesGenerator extends SpiGenerator {
    JavaFile generateFilePerType(ValidatedGeneratorElement valid, LazyvalEnvironment layzvalEnvironment);
}
