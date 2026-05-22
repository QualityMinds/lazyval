package com.qualityminds.lazyval.integration.boundary.rest;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;

import java.util.Locale;

/**
 * Wraps the provider's default message interpolator and re-interprets the two-argument
 * {@link #interpolate(String, Context)} call so it uses the per-request locale stored in
 * {@link RequestLocaleHolder} instead of {@code Locale.getDefault()}.
 *
 * Registered as {@code <message-interpolator>} in {@code META-INF/validation.xml}, hence
 * the public no-arg constructor.
 */
public class RequestLocaleMessageInterpolator implements MessageInterpolator {

    private final MessageInterpolator delegate;

    public RequestLocaleMessageInterpolator() {
        this.delegate = Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();
    }

    @Override
    public String interpolate(String messageTemplate, Context context) {
        Locale locale = RequestLocaleHolder.get();
        return locale != null
                ? delegate.interpolate(messageTemplate, context, locale)
                : delegate.interpolate(messageTemplate, context);
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        return delegate.interpolate(messageTemplate, context, locale);
    }
}
