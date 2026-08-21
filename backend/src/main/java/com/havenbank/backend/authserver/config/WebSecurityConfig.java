package com.havenbank.backend.authserver.config;

import com.havenbank.backend.authserver.controller.MfaAuthenticationSuccessHandler;
import com.havenbank.backend.authserver.login.LoginAttemptFilter;
import com.havenbank.backend.authserver.login.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;

/**
 * Form-login + OTP security for interactive authentication, plus the catch-all for any non-API,
 * non-protocol request. Ordered after the authorization-server chain ({@code @Order(1)}) and the
 * resource-server API chain ({@code @Order(2)}).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final MfaAuthenticationSuccessHandler mfaSuccessHandler;
    private final LoginAttemptService loginAttemptService;


    @Bean
    @Order(3)
    public SecurityFilterChain webFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RequestCache requestCache) throws Exception {
        http
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .requestCache(rc -> rc.requestCache(requestCache))
                // No .cors() here on purpose: this chain serves the login and OTP pages, static assets
                // and Swagger, all of which are ordinary same-origin navigations.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login/otp", "/error", "/actuator/health/**",
                                "/css/**", "/js/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(mfaSuccessHandler)   // password step; OTP handled downstream
                        .failureUrl("/login?error")
                        .permitAll())
                .addFilterBefore(new LoginAttemptFilter(loginAttemptService),
                        UsernamePasswordAuthenticationFilter.class);
        // CSRF remains enabled (default) for these browser-facing form endpoints.
        return http.build();
    }
}