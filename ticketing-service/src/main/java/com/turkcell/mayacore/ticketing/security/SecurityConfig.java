package com.turkcell.mayacore.ticketing.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayHeaderAuthFilter gatewayHeaderAuthFilter;
    private final TrustedGatewayHeadersFilter trustedGatewayHeadersFilter;

    public SecurityConfig(GatewayHeaderAuthFilter gatewayHeaderAuthFilter,
                          TrustedGatewayHeadersFilter trustedGatewayHeadersFilter) {
        this.gatewayHeaderAuthFilter = gatewayHeaderAuthFilter;
        this.trustedGatewayHeadersFilter = trustedGatewayHeadersFilter;
    }

    @Bean
    public FilterRegistrationBean<TrustedGatewayHeadersFilter> disableTrustedGatewayServletRegistration(
            TrustedGatewayHeadersFilter filter) {
        FilterRegistrationBean<TrustedGatewayHeadersFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<GatewayHeaderAuthFilter> disableGatewayHeaderServletRegistration(
            GatewayHeaderAuthFilter filter) {
        FilterRegistrationBean<GatewayHeaderAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PathPatternRequestMatcher.pathPattern("/h2-console/**"))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/events/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(trustedGatewayHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
