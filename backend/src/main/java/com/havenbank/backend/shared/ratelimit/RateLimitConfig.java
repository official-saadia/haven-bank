package com.havenbank.backend.shared.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers {@link RateLimitFilter} ahead of the Spring Security filter chain so IP-based limits
 * protect the login and token endpoints before authentication runs. Toggle with
 * {@code app.ratelimit.enabled}.
 */
@Configuration
@ConditionalOnProperty(name = "app.ratelimit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiter rateLimiter, TierResolver tierResolver, ApplicationEventPublisher events,
            RateLimitProperties properties, RequestMappingHandlerMapping requestMappingHandlerMapping) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(rateLimiter, tierResolver, events, properties, requestMappingHandlerMapping));
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}