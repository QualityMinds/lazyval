package com.qualityminds.lazyval.integration.boundary.rest

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.container.ContainerResponseContext
import jakarta.ws.rs.container.ContainerResponseFilter
import jakarta.ws.rs.ext.Provider

/**
 * Resolves the request locale from the `Accept-Language` header and stores it in
 * [RequestLocaleHolder] so [RequestLocaleMessageInterpolator] can pick it up when
 * interpolating constraint violation messages.
 */
@Provider
class LocaleRequestFilter : ContainerRequestFilter, ContainerResponseFilter {

    override fun filter(requestContext: ContainerRequestContext) {
        val resolved = requestContext.acceptableLanguages
            .firstOrNull { it.language != "*" }
        RequestLocaleHolder.set(resolved)
    }

    override fun filter(
        requestContext: ContainerRequestContext,
        responseContext: ContainerResponseContext,
    ) {
        RequestLocaleHolder.clear()
    }
}
