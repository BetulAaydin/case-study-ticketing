package com.turkcell.mayacore.apigateway.config;

import com.turkcell.mayacore.apigateway.converter.JwtUserSessionConverter;
import com.turkcell.mayacore.apigateway.filter.RateLimitFilter;
import com.turkcell.mayacore.apigateway.filter.UserHeaderForwardingFilter;
import com.turkcell.mayacore.commonlibrary.security.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final List<String> PERMITTED_PATHS = List.of(
            "/api/auth/**", "/actuator/**"
    );

    private final JwtUserSessionConverter jwtUserSessionConverter;
    private final UserHeaderForwardingFilter userHeaderForwardingFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtUserSessionConverter jwtUserSessionConverter,
                          UserHeaderForwardingFilter userHeaderForwardingFilter,
                          RateLimitFilter rateLimitFilter,
                          AuthenticationEntryPoint authenticationEntryPoint,
                          AccessDeniedHandler accessDeniedHandler) {
        this.jwtUserSessionConverter = jwtUserSessionConverter;
        this.userHeaderForwardingFilter = userHeaderForwardingFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Reservation rules MUST stay above /api/events/** (otherwise CUSTOMER gets 403).
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PERMITTED_PATHS.toArray(new String[0])).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/events/public", "/api/events/public/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/events/{eventId}/reservations",
                        "/api/events/*/reservations").hasAuthority("CUSTOMER")
                .requestMatchers(HttpMethod.POST,
                        "/api/reservations/{id}/confirm",
                        "/api/reservations/*/confirm").hasAuthority("CUSTOMER")
                .requestMatchers(HttpMethod.POST,
                        "/api/reservations/{id}/cancel",
                        "/api/reservations/*/cancel").hasAuthority("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/events/**").hasAnyAuthority("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/events/**").hasAnyAuthority("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/events/**").hasAnyAuthority("ORGANIZER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/events").authenticated()
                .anyRequest().authenticated()
        );

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
        );

        http.oauth2ResourceServer(oauth ->
                oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtUserSessionConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
        );

        http.addFilterAfter(userHeaderForwardingFilter, AuthorizationFilter.class);
        http.addFilterAfter(rateLimitFilter, UserHeaderForwardingFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        byte[] decoded = Base64.getDecoder().decode(jwtProperties.getSecret());
        SecretKeySpec secretKey = new SecretKeySpec(decoded, jwtProperties.getAlgorithm());
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
