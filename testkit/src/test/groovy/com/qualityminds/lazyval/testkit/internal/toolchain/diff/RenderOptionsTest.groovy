package com.qualityminds.lazyval.testkit.internal.toolchain.diff

import spock.lang.Specification

import java.util.function.UnaryOperator

class RenderOptionsTest extends Specification {

    UnaryOperator<String> props(Map<String, String> map) {
        return { String key -> map.get(key) } as UnaryOperator<String>
    }

    UnaryOperator<String> env(Map<String, String> map) {
        return { String key -> map.get(key) } as UnaryOperator<String>
    }

    def "system property forces color: #propValue -> #expected"() {
        expect:
        RenderOptions.detectAnsi(props([(RenderOptions.PROP_COLOR): propValue]), env([:])) == expected

        where:
        propValue | expected
        "true"    | true
        "TRUE"    | true
        "false"   | false
        "FALSE"   | false
    }

    def "system property overrides CI and NO_COLOR"() {
        expect:
        RenderOptions.detectAnsi(
                props([(RenderOptions.PROP_COLOR): "false"]),
                env(["CI": "true", "NO_COLOR": "1"])) == false
        RenderOptions.detectAnsi(
                props([(RenderOptions.PROP_COLOR): "true"]),
                env(["NO_COLOR": "1"])) == true
    }

    def "NO_COLOR wins over CI when property is unset"() {
        expect:
        RenderOptions.detectAnsi(props([:]), env(["CI": "true", "NO_COLOR": "1"])) == false
    }

    def "CI env enables color when property and NO_COLOR are unset"() {
        expect:
        RenderOptions.detectAnsi(props([:]), env(["CI": "true"])) == true
    }

    def "empty CI env is treated as unset"() {
        expect:
        RenderOptions.detectAnsi(props([:]), env(["CI": ""])) == false
    }

    def "nothing set: default is plain"() {
        expect:
        RenderOptions.detectAnsi(props([:]), env([:])) == false
    }

    def "property value 'auto' falls through to env detection"() {
        expect:
        RenderOptions.detectAnsi(props([(RenderOptions.PROP_COLOR): "auto"]), env(["CI": "true"])) == true
        RenderOptions.detectAnsi(props([(RenderOptions.PROP_COLOR): "auto"]), env([:])) == false
    }
}
