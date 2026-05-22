package com.qualityminds.lazyval.integration.boundary.rest;

import org.springframework.boot.validation.MessageInterpolatorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocaleContextMessageInterpolator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class LocaleConfig {

    /**
     * Replaces the default validator so constraint messages are resolved against the
     * request locale held in {@link org.springframework.context.i18n.LocaleContextHolder},
     * which Spring populates from the {@code Accept-Language} header via the default
     * {@link org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver}.
     *
     * The inner interpolator comes from {@link MessageInterpolatorFactory} so Spring's
     * {@code MessageSource} integration (project-level {@code messages.properties}) is
     * preserved.
     */
    @Bean
    public LocalValidatorFactoryBean localeAwareValidator() {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setMessageInterpolator(
                new LocaleContextMessageInterpolator(new MessageInterpolatorFactory().getObject()));
        return factory;
    }
}
