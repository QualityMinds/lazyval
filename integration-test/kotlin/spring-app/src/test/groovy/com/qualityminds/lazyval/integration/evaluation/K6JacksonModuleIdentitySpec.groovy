package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.LazyvalJacksonModule
import com.qualityminds.lazyval.integration.shared.Isbn
import spock.lang.Specification
import spock.lang.Title
import tools.jackson.core.JsonGenerator
import tools.jackson.core.Version
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

/**
 * AI evaluation finding K6 (GitHub issue "Evaluation: ksp", label evaluation). This specification intentionally
 * fails on main: it documents the expected behaviour, not the current one.
 * <p>
 * Every generated Jackson module passes the same fixed name to {@code SimpleModule}
 * ({@code "LazyvalJacksonModule"}, {@code "LazyvalJackson2Module"} for Jackson 2), and an explicit name is the
 * module's registration id. Two Lazyval-processed modules on one classpath, e.g. a shared domain jar and the
 * application, therefore register under one id, and Jackson keeps only one of them: Jackson 3's
 * {@code MapperBuilder.addModule} replaces the earlier module, Jackson 2 ignores the later one. The other module's
 * domain-primitives silently fall back to bean (de)serialization, which also bypasses their factories on read.
 * <p>
 * The second module is a stand-in with the name every generated Jackson 3 module carries, registering a serializer
 * for a type of its own. No application context: a plain {@link JsonMapper} with both modules added, as
 * {@code findAndAddModules()} or two module beans do it.
 */
@Title("Evaluation K6 - identity of the generated Jackson module")
class K6JacksonModuleIdentitySpec extends Specification {

    void "K6 the generated module and a second Lazyval module both take effect on one mapper"() {
        given:
        def mapper = JsonMapper.builder()
                .addModule(new LazyvalJacksonModule())
                .addModule(new OtherModulesLazyvalJacksonModule())
                .build()

        expect: 'both modules are registered'
        mapper.registeredModules()*.getClass().containsAll([LazyvalJacksonModule, OtherModulesLazyvalJacksonModule])

        and: 'each writes its own domain-primitive as a scalar'
        mapper.writeValueAsString(Isbn.parse("978-3-16-148410-0")) == '"978-3-16-148410-0"'
        mapper.writeValueAsString(new Sku("ABC-1234")) == '"ABC-1234"'
    }

    /** What KSP generates as {@code LazyvalJacksonModule} in another module, e.g. a shared domain jar. */
    static class OtherModulesLazyvalJacksonModule extends SimpleModule {

        OtherModulesLazyvalJacksonModule() {
            super("LazyvalJacksonModule", Version.unknownVersion())
            addSerializer(Sku, new SkuSerializer())
        }
    }

    /** A domain-primitive of that other module. */
    static class Sku {
        final String value

        Sku(String value) {
            this.value = value
        }
    }

    static class SkuSerializer extends StdSerializer<Sku> {

        SkuSerializer() {
            super(Sku)
        }

        @Override
        void serialize(Sku sku, JsonGenerator gen, SerializationContext provider) {
            gen.writeString(sku.value)
        }
    }
}
