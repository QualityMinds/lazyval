package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;
import de.qualityminds.lazyval.processor.LazyvalEnvironment;
import de.qualityminds.lazyval.processor.ValidatedGeneratorElement;

import java.util.Optional;
import java.util.Set;

/**
 * A service provider interface which is called by the {@link de.qualityminds.lazyval.processor.LazyvalProcessor} to
 * generate a single file for all domain primitives annotated with {@link de.qualityminds.lazyval.LazyValue}.
 * <p>
 * As an example, for all domain primitives only a single Mapstruct Mapper definition is needed.
 * </p>
 */
public non-sealed interface SingleFileGenerator extends SpiGenerator {

    Optional<JavaFile> generateSingleFile(Set<ValidatedGeneratorElement> elements, LazyvalEnvironment layzvalEnvironment);
}
