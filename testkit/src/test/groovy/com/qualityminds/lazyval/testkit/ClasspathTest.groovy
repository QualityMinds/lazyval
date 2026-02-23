package com.qualityminds.lazyval.testkit


import spock.lang.Specification

/**
 * There is not much we can test in this module due to chicken-egg problem (cycles between processor/ksp <-> testkit)
 * Dynamic classloading from "target/classes" also doesnt work in a clean reactor build. Hence, the Testkit is tested
 * by using it for testing in processor/ksp.
 */
class ClasspathTest extends Specification {

    void "Trying to access the Java Testkit when the Java processor is not on the classpath fails"(){
        when:
        Testkit.java()

        then:
        thrown(IllegalStateException)
    }

    void "Trying to access the Kotlin Testkit the Kotlin processor is not on the classpath fails"(){
        when:
        Testkit.kotlin()

        then:
        thrown(IllegalStateException)
    }
}