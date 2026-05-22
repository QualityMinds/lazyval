package com.qualityminds.lazyval.integration.boundary.rest

import jakarta.validation.MessageInterpolator
import jakarta.validation.Validation
import java.util.Locale

/**
 * Wraps the provider's default message interpolator and re-interprets the two-argument
 * [interpolate] call so it uses the per-request locale stored in [RequestLocaleHolder]
 * instead of `Locale.getDefault()`.
 *
 * Registered as `<message-interpolator>` in `META-INF/validation.xml`, hence the public
 * no-arg constructor.
 */
class RequestLocaleMessageInterpolator : MessageInterpolator {

    private val delegate: MessageInterpolator =
        Validation.byDefaultProvider().configure().defaultMessageInterpolator

    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context): String {
        val locale = RequestLocaleHolder.get()
        return if (locale != null) {
            delegate.interpolate(messageTemplate, context, locale)
        } else {
            delegate.interpolate(messageTemplate, context)
        }
    }

    override fun interpolate(
        messageTemplate: String,
        context: MessageInterpolator.Context,
        locale: Locale,
    ): String = delegate.interpolate(messageTemplate, context, locale)
}
