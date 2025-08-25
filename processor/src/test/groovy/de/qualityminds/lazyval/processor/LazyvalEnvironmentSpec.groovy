package de.qualityminds.lazyval.processor


import spock.lang.Specification

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.util.Elements

class LazyvalEnvironmentSpec extends Specification {

    void "UserSettings map AnnotationProcessor options correct"(){
        given:
        def configuredJpaPackage = "a.b"
        def configuredMapstructPackage = "c.d"
        def processingEnv = Mock(ProcessingEnvironment) {
            getElementUtils() >> Mock(Elements)
            getOptions() >> [
                    (LazyvalEnvironment.JPA_GENERATED_PACKAGE) : configuredJpaPackage,
                    (LazyvalEnvironment.MAPSTRUCT_GENERATED_PACKAGE) : configuredMapstructPackage
            ]
        }

        def sut = new LazyvalEnvironment(processingEnv)

        expect:
        sut.getSettings().getJpaConverterPackage().get() == configuredJpaPackage

        and:
        sut.getSettings().getMapstructPackage().get() == configuredMapstructPackage
    }
}