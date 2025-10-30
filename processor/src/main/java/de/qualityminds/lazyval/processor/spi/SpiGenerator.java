package de.qualityminds.lazyval.processor.spi;

import com.palantir.javapoet.JavaFile;
import de.qualityminds.lazyval.processor.LazyvalEnvironment;
import de.qualityminds.lazyval.processor.ValidatedGeneratorElement;

import java.util.Set;
import java.util.stream.Stream;

public sealed interface SpiGenerator permits SingleFileGenerator, MultipleFilesGenerator {

    default Stream<JavaFile> generate(Set<ValidatedGeneratorElement> elements, LazyvalEnvironment layzvalEnvironment){
        if(this instanceof SingleFileGenerator singleFileGenerator){
            return singleFileGenerator.generateSingleFile(elements, layzvalEnvironment).stream();
        }else if(this instanceof MultipleFilesGenerator multipleFilesGenerator){
            return elements.stream().map(element -> multipleFilesGenerator.generateFilePerType(element, layzvalEnvironment));
        }else{
            // move to switch-pattern-match once Java 21 is the minimum required version
            throw new IllegalStateException("Unknown generator type: " + this.getClass().getName());
        }
    }

}
