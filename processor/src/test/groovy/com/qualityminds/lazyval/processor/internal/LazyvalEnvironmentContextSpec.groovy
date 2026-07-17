package com.qualityminds.lazyval.processor.internal


import spock.lang.Specification

import java.util.function.Supplier

class LazyvalEnvironmentContextSpec extends Specification {

    void "Package for generator computed as '#expected' when #message"() {
        given:
        def fallbackSupplier = Mock(Supplier) {
            timesFallbackUsed * get() >> fallback
        }

        expect:
        PackageLookup.computePackage(config, override, fallbackSupplier) == expected

        where:
        message                                | basePackage | defaultLayer | override   || expected
        "no basePackage or override"           | null        | null         | null       || "fallback"
        "basePackage overridden"               | "base"      | "default"    | "override" || "override"
        "overridden basePackage without layer" | "base"      | null         | "override" || "override"
        "no config but override"               | null        | null         | "override" || "override"
        "basePackage with layer"               | "base"      | "default"    | null       || "base.default"
        "basePackage without layer"            | "base"      | null         | null       || "base"
        "only layer"                           | null        | "default"    | null       || "fallback"
        "only layer with override"             | null        | "default"    | "override" || "override"
        fallback = "fallback"
        config = PackageLookup.DefaultConfig.of(basePackage, defaultLayer)
        timesFallbackUsed = config == null && override == null ? 1 : 0
    }
}