package com.qualityminds.lazyval.integration.evaluation

import com.qualityminds.lazyval.integration.LazyvalJacksonModule
import com.qualityminds.lazyval.integration.shared.Isbn
import spock.lang.Specification
import spock.lang.Title
import tools.jackson.core.JsonGenerator
import tools.jackson.core.Version
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.ser.std.StdSerializer

/**
 * Documents AI evaluation finding P5 (GitHub issue "Evaluation: processor", label evaluation); the features
 * intentionally fail on main.
 * <p>
 * Every generated module calls {@code super("LazyvalJacksonModule", ...)} (Jackson 2: {@code "LazyvalJackson2Module"}),
 * and a {@code SimpleModule} with an explicit name uses that name as its registration id. Jackson keeps one module per
 * id — Jackson 3 the one added last, Jackson 2 the one registered first — so of two Lazyval modules on one mapper (two
 * jars of a modulith, each with its own generated module, both found by {@code findAndAddModules()} or registered as
 * beans) only one takes effect, depending on the order, and the types of the other silently fall back to bean
 * (de)serialization. Every generated module has to take effect.
 * <p>
 * The second module is a stand-in for the one generated into another jar: it names itself the way the generator names
 * every module today and registers a serializer for a domain-primitive of its own.
 */
@Title("Evaluation P5 - identity of generated Jackson modules")
class JacksonModuleIdentityEvaluationSpec extends Specification {

    def "P5 two Lazyval modules on one mapper both take effect when #order"() {
        given:
        def builder = JsonMapper.builder()
        modules.each { JacksonModule module -> builder.addModule(module) }
        def mapper = builder.build()

        expect: 'each module serializes its own domain-primitive as a scalar'
        mapper.writeValueAsString(Isbn.parse('978-3-16-148410-0')) == '"978-3-16-148410-0"'
        mapper.writeValueAsString(new Sku('ABC-1234')) == '"ABC-1234"'

        where:
        order                                   | modules
        'the generated module is added first'   | [new LazyvalJacksonModule(), new OtherJarLazyvalJacksonModule()]
        'the generated module is added last'    | [new OtherJarLazyvalJacksonModule(), new LazyvalJacksonModule()]
    }

    /** A domain-primitive of the other jar. */
    static final class Sku {
        final String value

        Sku(String value) {
            this.value = value
        }
    }

    /** What the generator emits for the other jar, reduced to the serializer of its one domain-primitive. */
    static class OtherJarLazyvalJacksonModule extends SimpleModule {

        OtherJarLazyvalJacksonModule() {
            super("LazyvalJacksonModule", Version.unknownVersion())
            addSerializer(Sku, new StdSerializer<Sku>(Sku) {
                @Override
                void serialize(Sku value, JsonGenerator gen, SerializationContext provider) {
                    gen.writeString(value.value)
                }
            })
        }
    }
}
