package com.qualityminds.lazyval.integration.boundary.rest

import org.springframework.boot.validation.MessageInterpolatorFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocaleContextMessageInterpolator
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

@Configuration
class LocaleConfig {

    /**
     * Replaces the default validator so constraint messages are resolved against the
     * request locale held in [org.springframework.context.i18n.LocaleContextHolder],
     * which Spring populates from the `Accept-Language` header via the default
     * [org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver].
     *
     * The inner interpolator comes from [MessageInterpolatorFactory] so Spring's
     * `MessageSource` integration (project-level `messages.properties`) is preserved.
     */
    @Bean
    fun localeAwareValidator(): LocalValidatorFactoryBean = LocalValidatorFactoryBean().apply {
        messageInterpolator = LocaleContextMessageInterpolator(MessageInterpolatorFactory().getObject())
    }
}
