package com.qualityminds.lazyval.integration.boundary.rest;

import java.util.Locale;

/**
 * Per-request locale storage populated by {@link LocaleRequestFilter} from the
 * {@code Accept-Language} header and consumed by {@link RequestLocaleMessageInterpolator}
 * when resolving Bean Validation messages. ThreadLocal is sufficient here because
 * Open Liberty's JAX-RS dispatches each request on a single thread.
 *
 * <p><b>Caveat:</b> if the resource methods are ever changed to return
 * {@code CompletionStage<Response>} or use {@code @Asynchronous}, validation may run
 * on a worker thread that never saw the filter — in that case, swap this for a
 * {@code @RequestScoped} CDI bean looked up via {@code CDI.current()} from the
 * interpolator.
 */
public final class RequestLocaleHolder {

    private static final ThreadLocal<Locale> CURRENT = new ThreadLocal<>();

    private RequestLocaleHolder() {}

    public static void set(Locale locale) {
        CURRENT.set(locale);
    }

    public static Locale get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
