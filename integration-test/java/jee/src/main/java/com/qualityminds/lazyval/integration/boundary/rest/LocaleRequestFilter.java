package com.qualityminds.lazyval.integration.boundary.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the request locale from the {@code Accept-Language} header and stores it in
 * {@link RequestLocaleHolder} so {@link RequestLocaleMessageInterpolator} can pick it up
 * when interpolating constraint violation messages.
 */
@Provider
public class LocaleRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        List<Locale> acceptable = requestContext.getAcceptableLanguages();
        Locale resolved = acceptable.stream()
                .filter(l -> !"*".equals(l.getLanguage()))
                .findFirst()
                .orElse(null);
        RequestLocaleHolder.set(resolved);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        RequestLocaleHolder.clear();
    }
}
