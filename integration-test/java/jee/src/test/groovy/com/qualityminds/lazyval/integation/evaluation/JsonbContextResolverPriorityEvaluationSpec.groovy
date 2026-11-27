package com.qualityminds.lazyval.integation.evaluation

import com.qualityminds.lazyval.integration.LazyvalJsonbContextResolver
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.ext.ContextResolver
import jakarta.ws.rs.ext.Provider
import spock.lang.Specification
import spock.lang.Title

/**
 * Documents AI evaluation finding P8 (GitHub issue "Evaluation: processor", label evaluation); the feature
 * intentionally fails on main.
 * <p>
 * The generated {@code LazyvalJsonbContextResolver} is an application {@code @Provider ContextResolver<Jsonb>}
 * without {@code @Priority}, so it ranks like the application's own {@code ContextResolver<Jsonb>}. Registered before
 * it, it replaces the application's JSON-B configuration — in the audit (RESTEasy mock dispatcher), an application
 * visibility policy was bypassed and a password hash serialized. The generated resolver has to yield to every
 * application resolver, i.e. declare a precedence lower than the {@code Priorities.USER} default.
 * <p>
 * Reflective rather than behavioral: a behavioral check needs a JAX-RS runtime, and RESTEasy's JAX-RS 3.1 API would
 * replace this module's Jakarta EE 11 {@code jakarta.ws.rs-api} through Maven's nearest-wins mediation — on the
 * compile classpath, too.
 */
@Title("Evaluation P8 - precedence of the generated JSON-B ContextResolver")
class JsonbContextResolverPriorityEvaluationSpec extends Specification {

    def "P8 the generated ContextResolver<Jsonb> declares a lower precedence than application providers"() {
        expect: 'the class this finding is about'
        LazyvalJsonbContextResolver.isAnnotationPresent(Provider)
        ContextResolver.isAssignableFrom(LazyvalJsonbContextResolver)

        when:
        def priority = LazyvalJsonbContextResolver.getAnnotation(Priority)

        then: 'a lower number wins, and providers without @Priority rank as Priorities.USER'
        priority != null
        priority.value() > Priorities.USER
    }
}
