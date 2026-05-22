package com.qualityminds.lazyval.integration.boundary.rest

import java.util.Locale

/**
 * Per-request locale storage populated by [LocaleRequestFilter] from the `Accept-Language`
 * header and consumed by [RequestLocaleMessageInterpolator] when resolving Bean Validation
 * messages. ThreadLocal is sufficient here because Open Liberty's JAX-RS dispatches each
 * request on a single thread.
 *
 * **Caveat:** if the resource methods are ever changed to return `CompletionStage<Response>`
 * or use `@Asynchronous`, validation may run on a worker thread that never saw the filter —
 * in that case, swap this for a `@RequestScoped` CDI bean looked up via `CDI.current()`
 * from the interpolator.
 */
object RequestLocaleHolder {
    private val current = ThreadLocal<Locale?>()

    fun set(locale: Locale?) {
        current.set(locale)
    }

    fun get(): Locale? = current.get()

    fun clear() {
        current.remove()
    }
}
